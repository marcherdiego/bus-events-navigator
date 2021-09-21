![JetBrains Plugins](https://img.shields.io/jetbrains/plugin/r/rating/17579) 
![GitHub code size in bytes](https://img.shields.io/github/languages/code-size/marcherdiego/bus-events-navigator)
![Downloads](https://img.shields.io/jetbrains/plugin/d/17579)


# EventBus Posters and Subscribers Navigator for Android Studio

[![Get from Marketplace](button.png "Download plugin")](https://plugins.jetbrains.com/plugin/17579-event-bus-navigator)

- Small plugin to keep track of event-posters for a given subscriber and the subscribers for a given event.
- This plugin if strongly tied to this <em>MVP library</em>, check it out: https://github.com/marcherdiego/android_mvp
- Navigate though posters and subscribers easily to keep track of your MVP flow.
- This plugin supports both Java and Kotlin and adapts to any kind of applications! Give it a try!
- It also provides the ability to see the Application's structure based on events posted and received by the presenters.

### Posters and Subscribers
Lines are marked with <img alt='Java' src='resources/icons/posters.svg'/> or <img alt='Java' src='resources/icons/subscribers.svg'/> depending on whether they `post` an event or `subscribe` to an event
|            Poster lines are identified with this icon            	|            Subscriber lines are identified with this icon            	|
|:----------------------------------------------------------------:	|:--------------------------------------------------------------------:	|
| <img alt='Java' src='resources/icons/posters.svg' height='100'/> 	| <img alt='Java' src='resources/icons/subscribers.svg' height='100'/> 	|

### Example usages
#### Java
<img alt='Java' src='gifs/java.gif'/>

#### Kotlin
<img alt='Kotlin' src='gifs/kotlin.gif'/>

#### Application's Architecture Overview
<img alt='Kotlin' src='gifs/structure.gif'/>
