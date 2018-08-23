/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.state

import com.tinder.StateMachine
import com.tinder.scarlet.ConfigFactory
import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.Message
import com.tinder.scarlet.Protocol
import com.tinder.scarlet.Topic

internal typealias ClientStateMachine = StateMachine<Client.State, Client.Event, Client.SideEffect>
internal typealias ConnectionStateMachine = StateMachine<Connection.State, Connection.Event, Connection.SideEffect>
internal typealias MessengerStateMachine = StateMachine<Messenger.State, Messenger.Event, Messenger.SideEffect>

class Coordinator(
    val configFactory: ConfigFactory,
    val lifecycle: Lifecycle,
    val protocolFactory: Protocol.Factory,
    val
) {

    private lateinit var clientStateCoordinator: ClientStateCoordinator
    private lateinit var protocolCoordinator: ProtocolCoordinator
    private lateinit var topicCoordinator: TopicCoordinator
    private lateinit var messageCoordinator: MessageCoordinator

    fun start() {

    }

    fun observeEvent() {

    }

    fun publish(topic: Topic, message: Message) {
        clientStateCoordinator.send(topic, message)
    }

    fun connect() {
        clientStateCoordinator.open()
    }

    fun disconnect() {
        clientStateCoordinator.close()
    }

    fun subscribe(topic: Topic) {
        clientStateCoordinator.subscribe(topic)
    }

    fun unsubscribe(topic: Topic) {
        clientStateCoordinator.unsubscribe(topic)
    }

    private inner class ClientStateCoordinator {
        private val clientStateMachine = Client.create { transition ->
            val sideEffect = transition.sideEffect!!
            when (sideEffect) {
                is Client.SideEffect.SendMessage -> {
                    messageCoordinator.sendAndRetry(sideEffect.topic, sideEffect.message)
                }
                is Client.SideEffect.FinishSendingMessage -> {
                    messageCoordinator.clear(sideEffect.topic, sideEffect.message)
                }

                is Client.SideEffect.ReceiveMessage -> {
                    // TODO write to subject?
                }
                is Client.SideEffect.Open -> {
                    protocolCoordinator.openAndRetry()
                    topicCoordinator.subscribeAndRetry(sideEffect.topics)
                    messageCoordinator.sendAndRetry(sideEffect.messages)

                }
                is Client.SideEffect.Close -> {
                    protocolCoordinator.close()
                    topicCoordinator.unsubscribe(sideEffect.topics)
                    messageCoordinator.clear(sideEffect.messages)
                }
                is Client.SideEffect.Subscribe -> {
                    topicCoordinator.subscribeAndRetry(sideEffect.topic)
                }
                is Client.SideEffect.Unsubscribe -> {
                    topicCoordinator.unsubscribe(sideEffect.topic)
                }
            }
        }

        fun send(topic: Topic, message: Message) {
            clientStateMachine.transition(Client.Event.OnShouldSendMessage(topic, message))
        }

        fun finishSending(topic: Topic, message: Message) {
            clientStateMachine.transition(Client.Event.OnShouldFinishSendingMessage(topic, message))
        }


        fun receive(topic: Topic, message: Message) {
            clientStateMachine.transition(Client.Event.OnShouldReceiveMessage(topic, message))
        }

        fun open() {
            clientStateMachine.transition(Client.Event.OnShouldOpen)
        }

        fun close() {
            clientStateMachine.transition(Client.Event.OnShouldClose)
        }

        fun subscribe(topic: Topic) {
            clientStateMachine.transition(Client.Event.OnShouldSubscribe(topic))
        }

        fun unsubscribe(topic: Topic) {
            clientStateMachine.transition(Client.Event.OnShouldUnsubscribe(topic))
        }

    }

    private inner class ProtocolCoordinator {
        var protocol: Protocol? = null
        private val connectionStateMachine = Connection.create(configFactory) { transition ->
            val sideEffect = transition.sideEffect!!
            when (sideEffect) {
                is Connection.SideEffect.ScheduleRetry -> {
                    // TODO timer
                }
                is Connection.SideEffect.UnscheduleRetry -> {
                    // TODO timer
                }
                is Connection.SideEffect.OpenConnection -> {
                    protocol = protocolFactory.create()
                    protocol?.open(ProtocolListener())
                }
                is Connection.SideEffect.CloseConnection -> {
                    protocol?.close()
                }
                is Connection.SideEffect.ForceCloseConnection -> {
                    protocol?.close()
                }
            }
        }

        fun openAndRetry() {
            connectionStateMachine.transition(Connection.Event.OnLifecycleStarted)
        }

        fun close() {
            connectionStateMachine.transition(Connection.Event.OnLifecycleStopped)
        }

        private inner class ProtocolListener : Protocol.Listener {
            override fun onProtocolOpened(
                clientOption: Any?,
                serverOption: Any?
            ) {
                connectionStateMachine.transition(
                    Connection.Event.OnConnectionOpened(
                        clientOption,
                        serverOption
                    )
                )
                topicCoordinator.start()
                messageCoordinator.start()
            }

            override fun onProtocolClosed(
                clientOption: Any?,
                serverOption: Any?
            ) {
                connectionStateMachine.transition(
                    Connection.Event.OnConnectionClosed(
                        clientOption,
                        serverOption
                    )
                )
                topicCoordinator.stop()
                messageCoordinator.stop()
            }

            override fun onProtocolFailed(error: Throwable) {
                connectionStateMachine.transition(
                    Connection.Event.OnConnectionFailed(
                        error
                    )
                )
                topicCoordinator.stop()
                messageCoordinator.stop()
            }

            override fun onMessageReceived(
                topic: Topic,
                message: Message,
                serverMessageInfo: Any?
            ) {
                clientStateCoordinator.receive(topic, message)
            }

            override fun onMessageSent(
                topic: Topic,
                message: Message,
                clientMessageInfo: Any?
            ) {
                messageCoordinator.onMessageSent(topic, message)
            }

            override fun onMessageFailedToSend(
                topic: Topic,
                message: Message,
                clientMessageInfo: Any?
            ) {
                messageCoordinator.onMessageFailedToSend(topic, message)

            }

            override fun onTopicSubscribed(topic: Topic) {
                topicCoordinator.onTopicSubscribed(topic)
            }

            override fun onTopicUnsubscribed(topic: Topic) {
                topicCoordinator.onTopicUnsubscribed(topic)
            }
        }
    }

    private inner class TopicCoordinator {
        private val topicStateMachines: MutableMap<Topic, ConnectionStateMachine> =
            emptyMap<Topic, ConnectionStateMachine>().toMutableMap()

        fun start() {
            topicStateMachines.forEach { (_, stateMachine) ->
                stateMachine.transition(Connection.Event.OnLifecycleStarted)
            }
        }

        fun stop() {
            topicStateMachines.forEach { (_, stateMachine) ->
                stateMachine.transition(Connection.Event.OnLifecycleStopped)
            }
        }

        fun subscribeAndRetry(topic: Topic) {
            subscribeAndRetry(setOf(topic))
        }

        fun subscribeAndRetry(topics: Set<Topic>) {
            topics.forEach { topic ->
                topicStateMachines[topic] = Connection.create(configFactory) { transition ->
                    val sideEffect = transition.sideEffect!!
                    when (sideEffect) {
                        is Connection.SideEffect.ScheduleRetry -> {
                            // TODO timer
                        }
                        is Connection.SideEffect.UnscheduleRetry -> {
                            // TODO timer
                        }
                        is Connection.SideEffect.OpenConnection -> {
                            // TODO make clientOption the protocol?
                            protocol.subscribe(sideEffect.option as Topic, null)
                        }
                        is Connection.SideEffect.CloseConnection -> {
                            protocol.unsubscribe(sideEffect.option as Topic, null)
                        }
                        is Connection.SideEffect.ForceCloseConnection -> {
                            protocol.unsubscribe(sideEffect.option as Topic, null)
                        }
                    }
                }
                topicStateMachines[topic]!!.transition(Connection.Event.OnLifecycleStarted)
            }
        }

        fun unsubscribe(topic: Topic) {
            unsubscribe(setOf(topic))
        }

        fun unsubscribe(topics: Set<Topic>) {
            topics.forEach { topic ->
                topicStateMachines[topic]!!.transition(Connection.Event.OnLifecycleStopped)
                topicStateMachines.remove(topic)
            }
        }

        fun onTopicSubscribed(topic: Topic) {
            topicStateMachines[topic]!!.transition(
                Connection.Event.OnConnectionOpened(
                    null,
                    null
                )
            )
        }

        fun onTopicUnsubscribed(topic: Topic) {
            topicStateMachines[topic]!!.transition(
                Connection.Event.OnConnectionClosed(
                    null,
                    null
                )
            )

        }
    }

    private inner class MessageCoordinator {
        private val messageGroupWorker = GroupWorker<Pair<Topic, Message>, Unit, Unit, Unit, Unit>()

        fun start() {
            messageGroupWorker.onLifecycleStarted()
        }

        fun stop() {
            messageGroupWorker.onLifecycleStopped()
        }

        fun sendAndRetry(topic: Topic, message: Message) {
            messageGroupWorker.add(topic to message, Unit, Unit) {
                val sideEffect2 = it.sideEffect!!
                when (sideEffect2) {
                    is Worker.SideEffect.ScheduleRetry -> {
                        // TODO timer
                    }
                    is Worker.SideEffect.UnscheduleRetry -> {
                        // TODO timer
                    }
                    is Worker.SideEffect.StartWork -> {
                        protocol.send(topic, message, null)
                    }
                    is Worker.SideEffect.StopWork -> {
                        clientStateCoordinator.finishSending(topic, message)
                    }
                }
            }
        }

        fun sendAndRetry(messages: Map<Topic, List<Message>>) {
            messages.forEach { (topic, messagesInTopic) ->
                messagesInTopic.forEach { message ->
                    sendAndRetry(topic, message)
                }
            }
        }

        fun clear(topic: Topic, message: Message) {
            messageGroupWorker.remove(topic to message)
        }

        fun clear(messages: Map<Topic, List<Message>>) {
            messages.forEach { (topic, messagesInTopic) ->
                messagesInTopic.forEach {
                    clear(topic, it)
                }
            }
        }

        fun onMessageSent(topic: Topic, message: Message) {
            messageGroupWorker.onWorkStopped(topic to message)
        }

        fun onMessageFailedToSend(topic: Topic, message: Message) {
            messageGroupWorker.onWorkFailed(topic to message, RuntimeException())
        }
    }

    data class MessageRequest(
        val protocol: Protocol,
        val topic: Topic,
        val message: Message
    )
}
