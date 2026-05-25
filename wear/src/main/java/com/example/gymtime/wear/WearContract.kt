package com.example.gymtime.wear

object WearContract {
    const val DATA_ACTIVE_SESSION = "/ironlog/active_session"

    const val MESSAGE_UPDATE_DRAFT = "/ironlog/command/update_draft"
    const val MESSAGE_LOG_SET = "/ironlog/command/log_set"
    const val MESSAGE_ADJUST_TIMER = "/ironlog/command/adjust_timer"
    const val MESSAGE_STOP_TIMER = "/ironlog/command/stop_timer"

    const val KEY_ACTIVE = "active"
    const val KEY_WORKOUT_ID = "workoutId"
    const val KEY_EXERCISE_ID = "exerciseId"
    const val KEY_EXERCISE_NAME = "exerciseName"
    const val KEY_TARGET_MUSCLE = "targetMuscle"
    const val KEY_LOG_TYPE = "logType"
    const val KEY_DISTANCE_UNIT = "distanceUnit"
    const val KEY_SET_NUMBER = "setNumber"
    const val KEY_WEIGHT = "weight"
    const val KEY_REPS = "reps"
    const val KEY_RPE = "rpe"
    const val KEY_DURATION = "duration"
    const val KEY_DISTANCE = "distance"
    const val KEY_CALORIES = "calories"
    const val KEY_IS_WARMUP = "isWarmup"
    const val KEY_REST_SECONDS = "restSeconds"
    const val KEY_TIMER_REMAINING_SECONDS = "timerRemainingSeconds"
    const val KEY_TIMER_RUNNING = "timerRunning"
    const val KEY_TIMER_COMPLETION_ID = "timerCompletionId"
    const val KEY_SET_SAVE_CONFIRMATION_ID = "setSaveConfirmationId"
    const val KEY_DELTA_SECONDS = "deltaSeconds"
}
