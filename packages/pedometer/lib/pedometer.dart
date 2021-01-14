import 'dart:async';

import 'package:flutter/services.dart';
import 'dart:io' show Platform;

const int _stopped = 0, _walking = 1;

class Pedometer {
  static const EventChannel _stepDetectionChannel =
      const EventChannel('step_detection');
  static const EventChannel _stepCountChannel =
      const EventChannel('step_count');
  static const EventChannel _altStepCountChannel =
      const EventChannel('alt_step_count');

  static const platform = const MethodChannel('pedometer/alt_pedometer');
  static var started = false;

  static StreamController<PedestrianStatus> _androidPedestrianController =
      StreamController.broadcast();

  /// Returns one step at a time.
  /// Events come every time a step is detected.
  static Stream<PedestrianStatus> get pedestrianStatusStream {
    Stream<PedestrianStatus> stream = _stepDetectionChannel
        .receiveBroadcastStream()
        .map((event) => PedestrianStatus._(event));
    if (Platform.isAndroid) return _androidStream(stream);
    return stream;
  }

  /// Transformed stream for the Android platform
  static Stream<PedestrianStatus> _androidStream(
      Stream<PedestrianStatus> stream) {
    /// Init a timer and a status
    Timer t;
    int pedestrianStatus;

    /// Listen for events on the original stream
    /// Transform these events by using the timer
    stream.listen((dynamic e) {
      /// If an event is received it means the status is 'walking'
      /// If the timer has been started, it should be cancelled
      /// to prevent sending out additional 'walking' events
      if (t != null) {
        t.cancel();

        /// If a previous status was either not set yet, or was 'stopped'
        /// then a 'walking' event should be emitted.
        if (pedestrianStatus == null || pedestrianStatus == _stopped) {
          _androidPedestrianController.add(PedestrianStatus._(_walking));
          pedestrianStatus = _walking;
        }
      }

      /// After receiving an event, start a timer for 2 seconds, after
      /// which a 'stopped' event is emitted. If it manages to go through,
      /// it is because no events were received for the 2 second duration
      t = Timer(Duration(seconds: 2), () {
        _androidPedestrianController.add(PedestrianStatus._(_stopped));
        pedestrianStatus = _stopped;
      });
    });

    return _androidPedestrianController.stream;
  }

  static Future<bool> get hasStepCounter async {
    var hasPedometer = true;
    await _stepCountChannel.receiveBroadcastStream().last.catchError(
        (Object err) =>
            hasPedometer = !err.toString().contains("not available"));
    return hasPedometer;
  }

  /// Returns the steps taken since last system boot.
  /// Events may come with a delay.
  static Stream<StepCount> get stepCountStream => _stepCountChannel
      .receiveBroadcastStream()
      .map((event) => StepCount._(event));

  /// users of this stream should call `stopPlatform` when they wish to
  /// end step tracking
  static Stream<StepCount> get altStepCountStream {
    if (!started) {
      _startPlatform();
    }
    return _altStepCountChannel
        .receiveBroadcastStream()
        .map((event) => StepCount._(event));
  }

  static void _startPlatform() {
    try {
      platform.invokeMethod('startPlatform');
      started = true;
    } on PlatformException catch (e) {
      print("Couldn't start platform code. ${e.message}");
    }
  }

  static void stopPlatform() {
    try {
      platform.invokeMethod('stopPlatform');
      started = false;
    } on PlatformException catch (e) {
      print("Couldn't stop platform code. ${e.message}");
    }
  }
}

/// A DTO for steps taken containing the number of steps taken.
class StepCount {
  DateTime _timeStamp;
  int _steps = 0;

  StepCount._(dynamic e) {
    _steps = e as int;
    _timeStamp = DateTime.now();
  }

  int get steps => _steps;

  DateTime get timeStamp => _timeStamp;

  @override
  String toString() =>
      'Steps taken: $_steps at ${_timeStamp.toIso8601String()}';
}

/// A DTO for steps taken containing a detected step and its corresponding
/// status, i.e. walking, stopped or unknown.
class PedestrianStatus {
  static const _WALKING = 'walking';
  static const _STOPPED = 'stopped';
  static const _UNKNOWN = 'unknown';

  static const Map<int, String> _STATUSES = {
    _stopped: _STOPPED,
    _walking: _WALKING
  };

  DateTime _timeStamp;
  String _status = _UNKNOWN;

  PedestrianStatus._(dynamic t) {
    int _type = t as int;
    _status = _STATUSES[_type];
    _timeStamp = DateTime.now();
  }

  String get status => _status;

  DateTime get timeStamp => _timeStamp;

  @override
  String toString() => 'Status: $_status at ${_timeStamp.toIso8601String()}';
}
