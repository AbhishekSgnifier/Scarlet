package com.tinder.app.socketio.chatroom.api

import com.tinder.app.socketio.chatroom.api.model.UserCountUpdate
import com.tinder.scarlet.ws.Receive
import io.reactivex.Flowable

interface UserLeftTopic {
    @Receive
    fun observeUserLeft(): Flowable<UserCountUpdate>
}