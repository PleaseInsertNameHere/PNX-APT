# PNX-APT

<img height="24" src="https://visitor-badge.laobi.icu/badge?page_id=PleaseInsertNameHere.PNX-APT"  alt="visitors badge"/>

**Annotation Processing Tool for [PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX) plugins.**

PNX-APT removes boilerplate from your plugin development by generating code at compile time. Instead of manually registering every listener, command, and config value — just annotate your classes and let the processor handle the rest.

---

## Features

- `@PluginMeta` — generates your `plugin.yml` from code
- `@AutoListener` — automatically registers all your event listeners
- `@Scheduler` — auto-registers and schedules tasks

### Planned in future releases:
- `@Command` — auto-registers commands
- `@Config` — maps config values into typed fields

---

## Installation

> PNX-APT is distributed via [JitPack](https://jitpack.io).

### Maven

Add the JitPack repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add the dependency:

```xml
<dependency>
    <groupId>com.github.pleaseinsertnamehere</groupId>
    <artifactId>pnx-apt</artifactId>
    <version>VERSION</version>
    <scope>provided</scope>
</dependency>
```

Configure the annotation processor in `maven-compiler-plugin`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.14.0</version>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>com.github.pleaseinsertnamehere</groupId>
                <artifactId>pnx-apt</artifactId>
                <version>VERSION</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

### Gradle

Add the JitPack repository to your `build.gradle`:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```

Add the dependency:

```groovy
dependencies {
    compileOnly 'com.github.pleaseinsertnamehere:pnx-apt:VERSION'
    annotationProcessor 'com.github.pleaseinsertnamehere:pnx-apt:VERSION'
}
```

---

## Usage

### Setup

PNX-APT generates source files at compile time. To make your IDE aware of the generated classes:

1. Run `mvn compile` once — this generates `PNXAPT` under `target/generated-sources/annotations/`
2. IntelliJ will automatically recognize the folder as a generated sources root

To avoid having to do this after every `mvn clean`, enable annotation processing in IntelliJ:  
`Settings` → `Build, Execution, Deployment` → `Compiler` → `Annotation Processors` → **Enable annotation processing**

## Init

You need to initialize the processor in your main plugin class. Just call `PNXAPT.init(this)` in `onEnable()`.

```java
public class MyPlugin extends PluginBase {

    @Override
    public void onEnable() {
        PNXAPT.init(this);
    }
}
```

### @PluginMeta

Annotate your main plugin class with `@PluginMeta` to generate a `plugin.yml` at compile time. No need to maintain a plugin.yml in `src/main/resources` manually — just delete it.

```java
@PluginMeta(
        name = "MyPlugin",
        version = "1.0.0",
        api = {"2.0.0"},
        authors = {"Steve"},
        description = "My cool plugin"
)
public class MyPlugin extends PluginBase {
    // Your code in here
}
```

| Field | Required | Description | 
|---|---|---|
| `name` | ✅ | Plugin name |
| `version` | ✅ | Plugin version |
| `api` | ✅ | Supported PNX API versions |
| `authors` | ❌ | Plugin authors |
| `description` | ❌ | Short description |
| `website` | ❌ | Plugin website |
| `prefix` | ❌ | Log prefix |
| `depend` | ❌ | Hard dependencies |
| `softDepend` | ❌ | Soft dependencies |
| `loadBefore` | ❌ | Plugins to load after this one |
| `order` | ❌ | Load order (`STARTUP` or `POSTWORLD`) |
| `features` | ❌ | PNX feature flags |

> **Note:** `@PluginMeta` can only be used on a class that extends `PluginBase`, and only once per project.

### @AutoListener

Annotate your listener classes with `@AutoListener` — no manual registration needed.

```java
import dev.pleaseinsertnamehere.pnxapt.annotations.AutoListener;

@AutoListener
public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage("Welcome!");
    }
}
```

That's it. Every class annotated with `@AutoListener` is automatically picked up and registered.

> **Note:** `@AutoListener` can only be used on classes that implement `cn.nukkit.event.Listener`.

### @Scheduler

Annotate Runnables with `@Scheduler` to have them automatically registered and scheduled.

```java
import dev.pleaseinsertnamehere.pnxapt.annotations.Scheduler;

@Scheduler(delay = 20, period = 100)
public class MyTask extends Task {
    @Override
    public void onRun(int currentTick) {
        // Task code here
    }
}
```

Or annotate any methods which are public static:

```java
import dev.pleaseinsertnamehere.pnxapt.annotations.Scheduler;

public class MyPlugin extends PluginBase {

    @Scheduler(delay = 20, period = 100, async = true)
    public static void myScheduledMethod() {
        // Task code here
    }
}
```

---

## Requirements

- Java 21+
- PowerNukkitX `2.0.0-SNAPSHOT` or later

---

## License

PNX-APT is licensed under [MIT](LICENSE).