# Who's There (Android)

Who's There is a sample video calling application built using the OpenTok
communications platform from [TokBox](https://tokbox.com).

Key features:
*  Make one-to-one calls.
*  Incoming calls display a live video preview of the caller before accepting (a la Knock Knock).
*  Respond to an incoming call even when the device is locked.
*  Automatic user registration.
*  Keep calls active while the application is in the background.
*  Compatible with devices back to Android API version 19.

**TODO**: screenshots

## Set up

This application requires several services working together to function:
*  Client application (such as this repository)
*  Server application (**TODO**: link)
*  OpenTok service (for voice and video communication)
*  Firebase service (for storage, authentication, and push messaging)

The following instructions guide you to set up this client application, but
you will also need to follow the set up instructions for the server application
to produce a complete and functional user experience.

1.  Clone this repository to your local machine. Import the project into
    Android Studio by browsing to the directory and selecting the `build.gradle`
    file. Allow for the Gradle sync to complete and resolve any build issues
    (e.g. downloading the necessary Android SDK version, build tools version,
    platform tools version, etc.).

2.  Follow the instructions to [add Firebase to your app](https://firebase.google.com/docs/android/setup#add_firebase_to_your_app).
    After completing those instructions, you will have a `google-services.json`
    file that you must place within the `app/` directory of this repository.

3.  Ensure the server application is set up and running.

4.  Run the app.