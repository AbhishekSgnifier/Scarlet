/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.v2.service

import com.tinder.StateMachine
import com.tinder.StateMachine.Matcher.Companion.any
import com.tinder.scarlet.v2.Event
import com.tinder.scarlet.v2.Lifecycle
import com.tinder.scarlet.v2.LifecycleState
import com.tinder.scarlet.v2.ProtocolEvent
import com.tinder.scarlet.v2.SideEffect
import com.tinder.scarlet.v2.State

class StateMachineFactory {

    fun create(): StateMachine<State, Event, SideEffect> {
        return StateMachine.create {
            initialState(State.Disconnected)
            state<State.Disconnected> {
                on(lifecycleStarted) {
                    transitionTo(
                        State.WillConnect(retryCount = 0),
                        SideEffect.ScheduleRetry(0)
                    )
                }
                on(lifecycleStopped) {
                    dontTransition()
                }
                on(lifecycleDestroyed) {
                    transitionTo(State.Destroyed)
                }
            }
            state<State.WillConnect> {
                on<Event.OnShouldConnect> {
                    transitionTo(
                        State.Connecting(retryCount = retryCount + 1),
                        SideEffect.OpenProtocol
                    )
                }
                on(lifecycleStarted) {
                    dontTransition()
                }
                on(lifecycleStopped) {
                    transitionTo(
                        State.Disconnected,
                        SideEffect.CancelRetry
                    )
                }
                on(lifecycleDestroyed) {
                    transitionTo(
                        State.Destroyed,
                        SideEffect.CancelRetry
                    )
                }
            }
            state<State.Connecting> {
                on(protocolOpened) {
                    transitionTo(State.Connected)
                }
                on(protocolFailed) {
                    transitionTo(
                        State.WillConnect(retryCount = retryCount + 1),
                        SideEffect.ScheduleRetry(retryCount)
                    )
                }
            }
            state<State.Connected> {
                on(lifecycleStarted) {
                    dontTransition()
                }
                on(lifecycleStopped) {
                    transitionTo(
                        State.Disconnecting,
                        SideEffect.CloseProtocol
                    )
                }
                on(lifecycleDestroyed) {
                    transitionTo(
                        State.Destroyed,
                        SideEffect.ForceCloseProtocol
                    )
                }
                on(protocolFailed) {
                    transitionTo(
                        State.WillConnect(retryCount = 0),
                        SideEffect.ScheduleRetry(0)
                    )
                }
            }
            state<State.Disconnecting> {
                on(protocolClosed) {
                    transitionTo(State.Disconnected)
                }
            }
            state<State.Destroyed> {
                // Terminal state
            }
        }
    }

    private companion object {
        private val lifecycleStarted =
            any<Event, Event.OnLifecycleStateChange>().where { lifecycleState == LifecycleState.Started }

        private val lifecycleStopped =
            any<Event, Event.OnLifecycleStateChange>().where { lifecycleState == LifecycleState.Stopped }

        private val lifecycleDestroyed =
            any<Event, Event.OnLifecycleStateChange>().where { lifecycleState == LifecycleState.Completed }

        private val protocolOpened =
            any<Event, Event.OnProtocolEvent>().where { protocolEvent is ProtocolEvent.OnOpened }

        private val protocolClosed =
            any<Event, Event.OnProtocolEvent>().where { protocolEvent is ProtocolEvent.OnClosed }

        private val protocolFailed =
            any<Event, Event.OnProtocolEvent>().where { protocolEvent is ProtocolEvent.OnFailed }
    }
}
