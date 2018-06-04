package com.rotor.notifications

import android.content.Context
import android.support.v4.app.NotificationCompat
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration
import com.rotor.core.Builder
import com.rotor.core.Rotor
import com.rotor.core.interfaces.BuilderFace
import com.rotor.database.Database
import com.rotor.database.abstr.Reference
import com.rotor.notifications.enums.Method
import com.rotor.notifications.model.*
import org.json.JSONObject
import java.util.*
import kotlin.collections.HashMap
import android.graphics.Bitmap
import android.view.View
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener
import android.support.v4.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NO_HISTORY
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.rotor.database.utils.ReferenceUtils
import com.rotor.notifications.data.NotificationDocker
import com.rotor.notifications.interfaces.ClazzLoader
import com.rotor.notifications.interfaces.Listener
import com.rotor.notifications.interfaces.Server
import com.rotor.notifications.request.NotificationGetter
import com.rotor.notifications.request.NotificationSender
import com.stringcare.library.SC
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.collections.ArrayList


/**
 * Created by efraespada on 18/03/2018.
 */
class Notifications {

    companion object {

        val ID = "id"
        val ROOM = "room"
        val RC = "request_code"
        val NOTIFICATION = "/notifications/"
        private  var docker: NotificationDocker? = null
        private  var listener: Listener? = null
        private val TAG: String = Notifications::class.java.simpleName!!
        private var loader: ClazzLoader<*> ? = null
        private var notificationStatusListener: NotificationRouterActivity.NotificationsStatus ? = null

        val api by lazy {
            service(Rotor.urlServer!!)
        }

        @JvmStatic fun <T> initialize(clazz: Class<T>, listener: Listener) {
            SC.init(Rotor.context)

            Database.initialize()

            loader = ClazzLoader<T>(clazz)
            this.listener = listener

            loadCachedNotifications()

            val config = ImageLoaderConfiguration.Builder(Rotor.context).build()
            ImageLoader.getInstance().init(config)


            Rotor.prepare(Builder.NOTIFICATION, object: BuilderFace {
                override fun onMessageReceived(jsonObject: JSONObject) {
                    try {
                        if (jsonObject.has("notifications")) {
                            val notification = jsonObject.getJSONObject("notifications")
                            if (notification.has("method") && notification.has("id")) {
                                val method = notification.getString("method")
                                if (Method.ADD.getMethod().equals(method)) {
                                    if (!docker!!.notifications!!.containsKey(notification.getString("id"))) {
                                        notify(notification.getString("id"))
                                    }
                                } else if (Method.REMOVE.getMethod().equals(method)) {
                                    if (docker!!.notifications!!.containsKey(notification.getString("id"))) {
                                        remove(NOTIFICATION + notification.getString("id"))
                                    }
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }

                }
            })

            if (notificationStatusListener != null) {
                notificationStatusListener!!.ready()
            }
        }

        @JvmStatic fun listener(notificationStatusListener: NotificationRouterActivity.NotificationsStatus) {
            this.notificationStatusListener  = notificationStatusListener
        }

        private fun service(url: String): Server {
            val retrofit = Retrofit.Builder()
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .baseUrl(url)
                    .build()

            return retrofit.create(Server::class.java)
        }

        @JvmStatic fun builder(content: Content ?, receivers: List<String>) : Notification {
            val id = Date().time
            val map = HashMap<String, Receiver>()
            for (receiver in receivers) {
                if (receiver.equals(Rotor.id)) {
                    continue
                }
                map[receiver] = Receiver(receiver, null)
            }
            content?.id = id.toString()
            return Notification(id.toString(), id, content, Sender(Rotor.id!!, id), map)
        }

        @JvmStatic fun notify(id: String) {
            notify(id, null)
        }

        @JvmStatic fun notify(notification: Notification) {
            notify(notification.id, notification)
        }

        @JvmStatic  private fun notify(id: String, notification: Notification ?) {
            val identifier = if (!id.contains("notifications")) NOTIFICATION + id else id
            Database.listen("notifications", identifier, object: Reference<Notification>(Notification::class.java) {

                var created = false

                override fun onCreate() {
                    notification?.let {
                        docker!!.notifications!![identifier] = notification
                        Database.sync(identifier)
                        Log.e(TAG, "notification added")
                        created = true
                    }
                }

                override fun onChanged(ref: Notification) {
                    Log.e(TAG, "notification returned")
                    var readCount = 0
                    val newOpens = ArrayList<String>()
                    if (ref.sender.id.equals(Rotor.id)) {
                        for (receiver in ref.receivers.values) {
                            if (receiver.viewed != docker!!.notifications!![identifier]!!.receivers[receiver.id]!!.viewed || (receiver.viewed != null && Rotor.id.equals(receiver.id))) {
                                newOpens.add(receiver.id)
                            }
                            if (receiver.viewed != null) {
                                readCount++
                            }
                        }
                    }

                    docker!!.notifications!![identifier] = ref
                    val gson = Gson()
                    ReferenceUtils.addElement(NOTIFICATION, gson.toJson(docker!!))
                    if (!ref.sender.id.equals(Rotor.id)) {
                        show(identifier)
                    }
                    if (created) {
                        created = false
                        var rece: ArrayList<Receiver> = arrayListOf()
                        rece.addAll(ref.receivers.values)
                        sendNotification(identifier, rece)
                    } else if (ref.receivers.containsKey(Rotor.id)
                            && ref.receivers[Rotor.id]!!.viewed != null
                            && !ref.sender!!.id!!.equals(Rotor.id)) {

                        val notificationManager = NotificationManagerCompat.from(Rotor.context!!)
                        val idNumber = ref.id.toLong()
                        notificationManager.cancel(idNumber.toInt())

                        docker!!.notifications!!.remove(identifier)
                        Database.unlisten(identifier)
                        ReferenceUtils.addElement(NOTIFICATION, gson.toJson(docker!!))
                    } else if (ref.sender.id.equals(Rotor.id) && listener != null) {
                        for (r in newOpens) {
                            listener!!.opened(r, docker!!.notifications!![identifier]!!)
                        }
                        if (readCount == docker!!.notifications!![identifier]!!.receivers.size) {
                            val notificationManager = NotificationManagerCompat.from(Rotor.context!!)
                            val idNumber = docker!!.notifications!![identifier]!!.id.toLong()
                            notificationManager.cancel(idNumber.toInt())
                            Database.remove(identifier)
                        }
                    }
                }

                override fun onUpdate(): Notification ? {
                    if (docker!!.notifications!!.containsKey(identifier)) {
                        return docker!!.notifications!!.get(identifier)
                    } else {
                        return null
                    }
                }

                override fun progress(value: Int) {
                    // nothing to do here
                }

                override fun onDestroy() {
                    if (docker != null &&
                        docker!!.notifications != null && docker!!.notifications!!.containsKey(identifier) &&
                        docker!!.notifications!![identifier]!!.sender.id.equals(Rotor.id)) {

                        listener?.let {
                            it.removed(docker!!.notifications!![identifier]!!)
                        }
                        docker!!.notifications!!.remove(identifier)
                        Database.unlisten(identifier)
                        val gson = Gson()
                        ReferenceUtils.addElement(NOTIFICATION, gson.toJson(docker!!))
                    }
                }

            })
        }

        @JvmStatic fun remove(value: String) : Boolean {
            var deleted = false
            for (notification in docker!!.notifications!!.values) {
                if (value.equals(notification.content.room)) {
                    if (notification.receivers.containsKey(Rotor.id)) {
                        notification.receivers.get(Rotor.id)!!.viewed = Date().time
                        Database.sync(NOTIFICATION + notification.id)
                        deleted = true
                    }
                }
            }
            if (!deleted) {
                val identifier = if (!value.contains("notifications")) NOTIFICATION + value else value
                if (docker!!.notifications!!.containsKey(identifier)) {
                    docker!!.notifications!![identifier]!!.receivers.get(Rotor.id)!!.viewed = Date().time
                    Database.sync(identifier)
                    deleted = true
                }
            }
            return deleted
        }

        @JvmStatic fun show(id: String) {
            var identifier = if (!id.contains("notifications")) NOTIFICATION + id else id
            if (docker!!.notifications!!.containsKey(identifier)) {
                val notification = docker!!.notifications!![identifier]

                val content = notification!!.content

                content?.let {
                    if (content.photo != null) {
                        val imageLoader = ImageLoader.getInstance()
                        imageLoader.loadImage(content.photo, object : SimpleImageLoadingListener() {
                            override fun onLoadingComplete(imageUri: String, view: View, loadedImage: Bitmap) {
                                interShow(identifier, content, loadedImage)
                            }
                        })
                    } else {
                        interShow(identifier, content, null)
                    }
                }
            }
        }

        private fun interShow(id: String, content: Content, bitmap: Bitmap ?) {
            var mBuilder: NotificationCompat.Builder ? = null
            if (bitmap != null) {
                mBuilder = NotificationCompat.Builder(Rotor.context!!, id)
                        .setSmallIcon(R.mipmap.ic_launcher_round)
                        .setLargeIcon(bitmap)
                        .setContentTitle(content.title)
                        .setContentText(content.body)
                        .setContentIntent(intentBuilder(content))
                        .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            } else {
                mBuilder = NotificationCompat.Builder(Rotor.context!!, id)
                        .setSmallIcon(R.mipmap.ic_launcher_round)
                        .setContentTitle(content.title)
                        .setContentText(content.body)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
                        .setContentIntent(intentBuilder(content))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            }



            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                if (content.channel != null && content.channelDescription != null) {
                    val name = content.channel
                    val description = content.channelDescription
                    val importance = NotificationManager.IMPORTANCE_DEFAULT
                    var channel: NotificationChannel ? = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        channel = NotificationChannel(id, name, importance)
                    }
                    channel!!.description = description

                    val notificationManager = Rotor.context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
                    if (notificationManager.areNotificationsEnabled()) {
                        var found = false
                        for (channels in notificationManager.notificationChannels) {
                            if (channels.id.equals(id)) {
                                found = true
                                break
                            }
                        }
                        if (!found) {
                            notificationManager.createNotificationChannel(channel)
                        }
                    }
                } else {
                    return
                }
            }

            val notificationManager = NotificationManagerCompat.from(Rotor.context!!)
            val idNumber = id.split("/")[2].toLong()
            notificationManager.notify(idNumber.toInt(), mBuilder.build())
        }

        private fun intentBuilder(content: Content) : PendingIntent {
            val resultIntent = Intent(Rotor.context, loader!!.getClazz())
            resultIntent.addFlags(FLAG_ACTIVITY_NO_HISTORY)
            resultIntent.putExtra(ID, content.id)
            resultIntent.putExtra(ROOM, content.room)
            resultIntent.putExtra(RC, content.requestCode)
            val resultPendingIntent = PendingIntent.getActivity(Rotor.context, 0, resultIntent, PendingIntent.FLAG_ONE_SHOT);
            return resultPendingIntent
        }

        @JvmStatic private fun loadCachedNotifications() {
            val notificationsAsString: String ? = ReferenceUtils.getElement(NOTIFICATION)
            if (notificationsAsString == null) {
                docker = NotificationDocker()
            } else {
                val gson = Gson()
                docker = gson.fromJson(notificationsAsString, NotificationDocker::class.java) as NotificationDocker
                for (notification in docker!!.notifications!!.values) {
                    for (receiver in notification.receivers.values) {
                        if (receiver.id.equals(Rotor.id) && receiver.viewed == null) {
                            notify(notification.id)
                        }
                    }
                    if (notification.sender.id.equals(Rotor.id, true)) {
                        notify(notification.id)
                    }
                }
            }
            getPendingNotification()
        }

        @JvmStatic private fun sendNotification(id: String, receivers: ArrayList<Receiver>) {
            api.sendNotification(NotificationSender("send_notifications", Rotor.id, id, receivers))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { result ->
                                result.status?.let {
                                    Log.e(TAG, result.status)
                                }
                            },
                            { error -> error.printStackTrace() }
                    )
        }

        @JvmStatic private fun getPendingNotification() {
            val receivers: ArrayList<String> = ArrayList()
            receivers.add(Rotor.id!!)
            api.getNotifications(NotificationGetter("pending_notifications", Rotor.id, receivers))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { result ->
                                result.status?.let {
                                    Log.e(TAG, result.status)
                                }
                            },
                            { error -> error.printStackTrace() }
                    )
        }

    }
}

