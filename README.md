# Phone Call Simulator - Android ML Kit Application

An Android application that uses ML Kit to detect face poses and simulate answering a phone call. The app transitions through different states based on user gestures detected through the camera.

## 🎯 Overview

This application demonstrates advanced use of Google's ML Kit for:
- **Face Detection**: Detecting when a person appears in front of the camera
- **Pose Detection**: Tracking hand and ear positions in real-time
- **Gesture Recognition**: Detecting when a user brings their hand or phone near their ear

The app simulates a phone ringing scenario and transitions to "answering" the call when it detects the user making a phone-answering gesture.

## 🏗️ Architecture

### State Machine Pattern

The application uses a state machine with three states:

1. **INITIAL**: Waiting for a face to be detected
2. **PHONE_RINGING**: Face detected, phone ringing, waiting for answering gesture
3. **PLAYING_VIDEO**: User answered the phone (hand/phone near ear detected)

```kotlin
enum class AppState {
    INITIAL,
    PHONE_RINGING,
    PLAYING_VIDEO
}
```

### Core Components

#### 1. Scene-Based Architecture

The application uses a scene-based approach to represent the visual state:

- **`Scene`**: Encapsulates a person in a single frame
- **`Sequence`**: Maintains a sliding window of up to 5 recent scenes for temporal analysis
- **`Person`**: Data class holding pose landmarks (ears, hands)

#### 2. Image Processing Pipeline

The app uses a strategy pattern for processing camera frames:

```
Camera Frame → ImageProcessor (state-specific) → Scene Creation → Analysis → State Transition
```

**Image Processors:**
- **`InitialImageProcessor`**: Uses face detection to detect when a person appears
- **`PhoneRingingImageProcessor`**: Uses pose detection to create complete scenes
- **`NoOpImageProcessor`**: Does nothing (used in terminal states)

#### 3. Analysis Components

- **`PoseObjectMapper`**: Transforms ML Kit coordinates to canvas coordinates
- **`SceneAnalyser`**: Analyzes individual scenes to determine pose states
  - `HAND_NEAR_EAR`
  - `HAND_AWAY_FROM_EAR`
  - `NO_PERSON`
  - `UNKNOWN`

- **`SceneSequenceAnalyser`**: Analyzes sequences of scenes over time
  - Uses 70% threshold: if 70% of recent scenes show hand/phone near ear, considers it valid

#### 4. Visualization

- **`MyCanvas`**: Custom View that renders:
  - Person landmarks (ears, hands) as blue circles
  - Detected object bounding boxes as red rectangles
  - Current pose state as text overlay

## 🚀 Features

### Real-time Detection
- Face detection using ML Kit Face Detection API
- Pose detection tracking 4 key landmarks: left ear, right ear, left hand, right hand

### Intelligent Gesture Recognition
- Detects hand-to-ear gesture (60px proximity threshold)
- Temporal smoothing: requires 70% of recent frames to confirm gesture
- Reduces false positives with sliding window analysis

### Audio Feedback
- Plays phone ringing sound on loop when in PHONE_RINGING state
- Stops audio on gesture detection or timeout

### Visual Debugging
- Real-time overlay showing detected landmarks
- Current pose state displayed on screen
- State labels showing current application state

## 📱 Technical Stack

- **Language**: Kotlin
- **Min SDK**: 33 (Android 13)
- **Target SDK**: 36
- **Architecture**: MVVM-like with State Pattern

**Note**: This application requires a device with a front-facing camera and Android 13+ to run.

