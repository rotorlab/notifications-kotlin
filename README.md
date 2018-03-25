[ ![Download](https://api.bintray.com/packages/efff/maven/RotorKotlinDatabase/images/download.svg) ](https://bintray.com/efff/maven/RotorKotlinDatabase/_latestVersion)
<p align="center"><img width="10%" vspace="20" src="https://github.com/rotorlab/notifications-kotlin/raw/develop/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png"></p>
 
Before use this library, [Rotor Core](https://github.com/rotorlab/core-kotlin) must be initialized. Lastest version is always the same on all Rotor libs.

# Rotor Notifications
Rotor Notifications is a complementary module for Rotor Core (kotlin). It allows to send notifications by using `Rotor.id` as device identifier.

Build and send notifications is very easy:
```java
Content content = new Content(ACTION_CHAT,  // requestCode
        "Hi :)",                            // title
        "Welcome to notifications!",        // body
        "ttt",                              // data
        "myChannel",                        // channel ID (Android 8)
        "Test channel",                     // channel description
        null,                               // small photo url
        null                                // big photo url
);                              
 
ArrayList<String> ids = new ArrayList<>();  // device ids will receive the notification
ids.add("f33f3642e39650b9");
ids.add("48484aad18e02d76");

Notification notification = Notifications.builder(content, ids);

Notifications.createNotification(notification.getId(), notification);
```

Implementing notifications is usually a confused task. Rotor makes it easier by defining a routing `Activity` for handler notification actions. First of all `Notifications` must be initialized with a routing activity `Class`:
```java
public class SplashActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Rotor.initialize(getApplicationContext(), BuildConfig.database_url, BuildConfig.redis_url, new StatusListener() {
            @Override
            public void connected() {
                // Database.initialize();
                Notifications.initialize(NotificationActivity.class);
            }
 
            @Override
            public void reconnecting() {
 
            }
        });
    }
}
```
Define a routing activity by extending `NotificationRouterActivity` and implement `onCreate` and `notificationTouched` methods:
```java
public class NotificationActivity extends NotificationRouterActivity {
    @Override
    public void onCreate(@org.jetbrains.annotations.Nullable Bundle savedInstanceState, int action, @NotNull String id, @NotNull String data) {
        Rotor.initialize(getApplicationContext(), BuildConfig.database_url, BuildConfig.redis_url, new StatusListener() {
            @Override
            public void connected() {
                // Database.initialize();
                Notifications.initialize(NotificationActivity.class);
            }
 
            @Override
            public void reconnecting() {
 
            }
        });
    }
 
    @Override
    public void notificationTouched(int action, @NotNull String id, @NotNull String data) {
        if (action == SplashActivity.ACTION_CHAT) {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.addFlags(FLAG_ACTIVITY_NO_HISTORY);
            intent.putExtra("path", data);
            intent.putExtra("notification", id);
            startActivity(intent);
        }
        finish();
    }
}
```

## Implementation
Import libraries:

```groovy
android {
    defaultConfig {
        multiDexEnabled true
    }
}
 
def rotor_version =  "0.1.2"
 
dependencies {
    implementation ("com.rotor:core:$rotor_version@aar") {
        transitive = true
    }
    implementation ("com.rotor:notifications:$rotor_version@aar") {
        transitive = true
    }
}
```
