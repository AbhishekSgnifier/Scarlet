/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.state

import com.tinder.StateMachine
import com.tinder.scarlet.Message
import com.tinder.scarlet.Topic

// Action<Payload>
object Messenger {

    fun create(
        topic: Topic,
        message: Message,
        listener: (StateMachine.Transition.Valid<State, Event, SideEffect>) -> Unit
    ): StateMachine<State, Event, SideEffect> {
        return StateMachine.create {
            initialState(State.Paused(topic, message))
            state<State.Paused> {
                on<Event.OnLifecycleStarted> {
                    transitionTo(
                        State.WillSend(topic, message, retryCount),
                        SideEffect.ScheduleRetry(0)
                    )
                }
            }
            state<State.WillSend> {
                on<Event.OnShouldSendMessage> {
                    transitionTo(
                        State.Sending(topic, message, retryCount),
                        SideEffect.SendMessage(topic, message)
                    )
                }
                on<Event.OnLifecycleStopped> {
                    transitionTo(
                        State.Paused(topic, message, retryCount),
                        SideEffect.UnscheduleRetry
                    )
                }
            }
            state<State.Sending> {
                on<Event.OnMessageSent> {
                    transitionTo(
                        State.Sent(topic, message),
                        SideEffect.MarkAsSent(topic, message)
                    )
                }
                on<Event.OnMessageFailed> {
                    transitionTo(
                        State.WillSend(topic, message, retryCount + 1),
                        SideEffect.ScheduleRetry(retryCount + 1)
                    )
                }
            }
            state<State.Sent> {
            }
            onTransition {
                if (it is StateMachine.Transition.Valid) {
                    listener(it)
                }
            }
        }
    }

    sealed class State {
        data class Paused internal constructor(
            val topic: Topic,
            val message: Message,
            val retryCount: Int = 0
        ) : State()

        data class WillSend internal constructor(
            val topic: Topic,
            val message: Message,
            val retryCount: Int = 0
        ) : State()

        data class Sending internal constructor(
            val topic: Topic,
            val message: Message,
            val retryCount: Int
        ) : State()

        data class Sent internal constructor(
            val topic: Topic,
            val message: Message
        ) : State()
    }

    sealed class Event {
        object OnLifecycleStarted : Event()
        object OnLifecycleStopped : Event()

        object OnShouldSendMessage : Event()
        object OnMessageSent : Event()
        object OnMessageFailed : Event()
    }

    sealed class SideEffect {
        data class ScheduleRetry(val retryCount: Int) : SideEffect()

        object UnscheduleRetry : SideEffect()

        data class SendMessage(val topic: Topic, val message: Message) : SideEffect()

        data class MarkAsSent(val topic: Topic, val message: Message) : SideEffect()
    }

}
