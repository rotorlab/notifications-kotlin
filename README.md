[ ![Download](https://api.bintray.com/packages/efff/maven/RotorKotlinDatabase/images/download.svg) ](https://bintray.com/efff/maven/RotorKotlinDatabase/_latestVersion)
<p align="center"><img width="10%" vspace="20" src="https://github.com/rotorlab/notifications-kotlin/raw/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png"></p>
 
Before use this library, [Rotor Core](https://github.com/rotorlab/core-kotlin) must be initialized. Lastest version is always the same on all Rotor libs.

# Rotor Notifications
Rotor Notifications is a complementary module for Rotor Core (kotlin). It allows to send notifications by using `Rotor.id` as device identifier.

## Implementation
Import libraries:

```groovy
android {
    defaultConfig {
        multiDexEnabled true
    }
}
 
def rotor_version =  "0.3"
 
dependencies {
    implementation ("com.rotor:core:$rotor_version@aar") {
        transitive = true
    }
    implementation ("com.rotor:notifications:$rotor_version@aar") {
        transitive = true
    }
}
```

## Build Notifications
Build and send notifications is very easy:
```java
Content content = new Content(ACTION_CHAT,  // requestCode
        "Hi :)",                            // title
        "Welcome to notifications!",        // body
        "my_room",                          // room
        "myChannel",                        // channel ID           (Android 8)
        "Test channel",                     // channel description  (Android 8)
        null,                               // small photo url
        null                                // big photo url
);                              
 
ArrayList<String> ids = new ArrayList<>();  // device ids that will receive the notification
ids.add(Rotor.id);
ids.add("48484aad18e02d76");

// send
Notifications.notify(Notifications.builder(content, ids));
```
## Initilize app
Implementing notifications is usually a confused task. Rotor makes it easier by defining a routing `Activity` for handler notification actions. First of all `Notifications` must be initialized with a routing activity `Class`:
```java
public class SplashActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Rotor.initialize(getApplicationContext(), BuildConfig.database_url, BuildConfig.redis_url, new StatusListener() {
            @Override
            public void connected() {
                // uncomment if needed: Database.initialize();
                Notifications.initialize(NotificationActivity.class, new Listener() {
                    @Override
                    public void opened(@NotNull String deviceId, @NotNull Notification notification) {
 
                    }
 
                    @Override
                    public void removed(@NotNull Notification notification) {
 
                    }
                });
            }
 
            @Override
            public void reconnecting() {
 
            }
        });
    }
}
```

## Initilize app with notifications routing
Define a routing activity by extending `NotificationRouterActivity` and implement `onCreate` and `notificationTouched` methods:
```java
public class NotificationActivity extends NotificationRouterActivity {
    @Override
    public void onCreate() {
        Rotor.initialize(getApplicationContext(), BuildConfig.database_url, BuildConfig.redis_url, new StatusListener() {
            @Override
            public void connected() {
                // Database.initialize();
                Notifications.initialize(NotificationActivity.class, new Listener() {
                    @Override
                    public void opened(@NotNull String deviceId, @NotNull Notification notification) {
 
                    }
 
                    @Override
                    public void removed(@NotNull Notification notification) {
 
                    }
                });
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

License
-------
     Copyright 2018 RotorLab Organization
 
     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at
 
        http://www.apache.org/licenses/LICENSE-2.0
 
     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
