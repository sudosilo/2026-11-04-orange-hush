Hush changelog

2026-11-23, v2026.11.23. Blocked notifications that refuse a plain cancel are now snoozed instead.

2026-11-20, v2026.11.20b. Tapping the Clock fixer badge while the fixer is off now opens the Hush app info page first, where Samsung's restricted setting gets unlocked, and the next tap opens the accessibility screen to switch the fixer on.

2026-11-20, v2026.11.20. Clock lock no longer posts any pop-up. When the clock moves off the chosen zone, the clock fixer puts it back silently. The fixer now waits after flipping automatic time zone off so it never flips it back on by a second tap.

2026-11-19, v2026.11.19b. Rebuilt the Hush screen so every state reads at a glance. A banner at the top says whether Hush is blocking and how many apps. Every setting shows a fixed size ON or OFF badge instead of a switch. Every app row shows BLOCKED in red or ALLOWED in green, and blocked rows are tinted red. The clock line shows in green when the clock is correct and in red when it is wrong. Nothing on the screen changes size or moves when a state changes.

2026-11-19, v2026.11.19. Added the clock fixer. When the phone moves the clock off the chosen zone, Hush opens the date and time screen by itself, turns automatic time zone off, picks the chosen zone, and goes back home. If the phone is locked it waits until the next unlock. It runs once it is switched on under the phone's accessibility settings.

2026-11-16, v2026.11.16. Added Clock lock. Hush holds the phone clock on a chosen time zone, Las Vegas by default. When anything turns automatic time zone back on, Hush turns it off again right away, and if the clock still lands on the wrong zone Hush posts an alert that opens the date and time screen. Flipping that setting off for you needs a one time permission granted over wireless debugging.

2026-11-04, v2026.11.04. Created Hush, an Android app that blocks notifications. Added a digest that holds blocked notifications and releases them together on a preset schedule, from every hour to once a day at 8 AM. The app is built into an installable APK automatically on every push.
