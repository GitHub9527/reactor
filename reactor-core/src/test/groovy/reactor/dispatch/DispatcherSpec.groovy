/*
 * Copyright (c) 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */





package reactor.dispatch

import static reactor.GroovyTestUtils.$
import static reactor.GroovyTestUtils.consumer

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import reactor.Fn
import reactor.filter.PassThroughFilter
import reactor.fn.Consumer
import reactor.fn.Event
import reactor.fn.dispatch.ConsumerFilteringEventRouter
import reactor.fn.dispatch.ArgumentConvertingConsumerInvoker
import reactor.fn.dispatch.SynchronousDispatcher
import reactor.fn.dispatch.ThreadPoolExecutorDispatcher
import reactor.fn.registry.CachingRegistry
import spock.lang.Specification

/**
 * @author Jon Brisbin
 * @author Stephane Maldini
 */
class DispatcherSpec extends Specification {

	def "Dispatcher executes tasks in correct thread"() {

		given:
		def sameThread = new SynchronousDispatcher()
		def diffThread = new ThreadPoolExecutorDispatcher(1,128)
		def currentThread = Thread.currentThread()
		Thread taskThread = null
		def registry = new CachingRegistry<Consumer<Event>>(null)
		def eventRouter = new ConsumerFilteringEventRouter(new PassThroughFilter(), ArgumentConvertingConsumerInvoker
				.DEFAULT)
		def sel = $('test')
		registry.register(sel, consumer {
			taskThread = Thread.currentThread()
		})

		when: "a task is submitted"
		def t = sameThread.nextTask()
		t.key = 'test'
		t.event = Event.wrap("Hello World!")
		t.consumerRegistry = registry
		t.eventRouter = eventRouter;
		t.submit()

		then: "the task thread should be the current thread"
		currentThread == taskThread

		when: "a task is submitted to the thread pool dispatcher"
		def latch = new CountDownLatch(1)
		t = diffThread.nextTask()
		t.key = 'test'
		t.event = Event.wrap("Hello World!")
		t.consumerRegistry = registry
		t.eventRouter = eventRouter;
		t.setCompletionConsumer({ Event<String> ev -> latch.countDown() } as Consumer<Event<String>>)
		t.submit()

		latch.await(5, TimeUnit.SECONDS) // Wait for task to execute

		then: "the task thread should be different when the current thread"
		taskThread != currentThread
		//!diffThread.shutdown()

	}

}
