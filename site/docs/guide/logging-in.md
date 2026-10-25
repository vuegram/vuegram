# Logging in

After your API ID and hash (see [Getting started](./getting-started#first-start)), VueGram asks you to sign in to
Telegram. There are two ways. The QR code is the easy one, and it comes first.

## Log in with a QR code (recommended)

1. The TV shows a code under **Scan to log in**.
2. On your phone, open **Telegram**.
3. Go to **Settings › Devices**.
4. Tap **Add Device** (called **Link Desktop Device** in older versions of Telegram) and point the phone's camera at the code on the TV.

That's it: the TV logs in and shows your library. The code refreshes itself until it is scanned, so there is no
rush.

### Why QR is the better way

When you log in with a phone number, Telegram sometimes puts a wall in the way:

- It may ask for a **login email** before it sends a code.
- It may ask for a **paid SMS** for your number.

The QR code needs neither. There is no SMS and no login email involved: your phone, which is already logged in,
vouches for the TV. When VueGram meets one of those walls on the phone-number route, it says so and offers the QR
code instead.

## Log in with your phone number

If you can't scan a code (no phone with Telegram nearby, for example), choose **Use a phone number instead**. VueGram
takes a second to switch (Telegram's library can't go from the QR code straight to a phone number), then:

1. Enter your **phone number**, with the country code, and choose **Send code**.
2. Telegram sends a **login code**, usually to your Telegram app. Enter it and choose **Sign in**.

If Telegram asks for a login email or a paid SMS at this point, VueGram says so: choose **Log in with a QR code**.

## 2-step verification

If your Telegram account has a 2-step verification password (Telegram's cloud password), the TV asks for it after
the QR code or the login code, and shows the hint you set for it. Enter it and choose **Sign in**. This is the password
you set in Telegram, not your phone's lock code.

## Logged in for good

You stay logged in, like on any other Telegram device. The TV shows up in Telegram on your phone under
**Settings › Devices**, where you can also end its session at any time.

If the login doesn't go through, see [Troubleshooting](./troubleshooting#login-is-stuck).
