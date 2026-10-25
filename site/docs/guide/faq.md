# FAQ

## Is VueGram an official Telegram app?

No. VueGram is an unofficial client built on Telegram's own library, TDLib. It is not made by, affiliated with or
endorsed by Telegram.

## How do I say it?

"View-gram".

## Does it cost anything?

No. VueGram is free and open source, under the MIT license.

## Which TVs does it work on?

Android TV and Google TV, with Android 6.0 or newer. See [Getting started](./getting-started#what-you-need).

## Is it on the Play Store?

No. You install it from its [GitHub releases](https://github.com/vuegram/vuegram/releases), and after that it
[updates itself](./updates).

## Why do I need my own API ID and hash?

Every app that talks to Telegram identifies itself with an API ID and hash. VueGram asks for your own, which you get
for free at [my.telegram.org](https://my.telegram.org/apps), so your copy talks to Telegram as itself. See
[Getting started](./getting-started#get-your-api-id-and-hash).

## Why log in with a QR code?

Because it needs no SMS and no login email. When you log in with a phone number, Telegram may first ask for a login
email or a paid SMS; scanning the QR code from Telegram on your phone avoids both. See [Logging in](./logging-in).

## Can I see my private chats?

By default, VueGram lists only groups and channels. Turn on [Show people](./settings#show-people) to also list your
private chats with people.

## Does it show photos and text messages?

By default a chat shows only its videos. Turn on [Show messages](./settings#show-messages) to see all of a chat's
messages.

## Do videos fill up my TV's storage?

No. Videos are streamed, and the parts you watch are deleted when you leave the player, when the next video starts
and when VueGram starts.

## Does anything go through a server other than Telegram's?

No. There are no bots and no third-party servers. Videos travel from Telegram to the app, and from the app to the
player through a small server inside VueGram on `127.0.0.1`. The only other request is the update check to GitHub.
See [Privacy & security](./privacy).

## Does VueGram collect analytics?

No. There is no analytics or tracking in the app, and none on vuegram.com or these docs.

## Where is my watch history stored?

On the TV only: where you stopped, what you have watched, and the chats you pinned or hid.

## A video won't play. What can I do?

Install [mpv-android](https://github.com/mpv-android/mpv-android/releases). VueGram's own player tries several
decoders first, and hands the rare file none of them can play to mpv. See
[Troubleshooting](./troubleshooting#a-video-won-t-play).

## How do I log out?

On the TV, open VueGram's [Settings](./settings#log-out) and choose **Log out**. You can also end the TV's session
from Telegram on your phone, under **Settings › Devices**.

## Why is it called VueGram and not something with "Telegram" in it?

Telegram's API terms don't allow third-party apps to use the Telegram name or its paper-plane logo as their own.
VueGram's logo is its own: a folded violet play button.

## I'm a developer. Where do I start?

At [Building from source](../developers/building).
