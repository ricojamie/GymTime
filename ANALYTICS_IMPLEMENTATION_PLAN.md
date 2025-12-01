# Analytics Page Implementation Plan

**Created:** December 1, 2025
**Status:** Ready for Implementation
**Target Version:** Analytics v1.0 (MVP)

---

## Executive Summary

This document outlines the complete plan for implementing the Analytics page in IronLog. The plan synthesizes input from three perspectives:
- **Project Manager**: Technical feasibility, database queries, implementation timeline
- **UX Critic**: Visual design, information hierarchy, interaction patterns
- **App User**: Real-world usage patterns, metric priorities, actionable insights

### Key Decisions Made
1. **Default view requires zero interaction** - Show most useful data immediately
2. **Recent trends matter more than all-time data** - Default to 12 weeks
3. **Mobile-first design** - Big numbers, deltas, minimal charts, thumb-friendly controls
4. **Frequency tracking is critical** - "Am I showing up?" is as important as "Am I lifting more?"
5. **Everything is free** - No freemium gates in v1.0

---

## User Decisions (Locked In)

### 1. Hero Card Metrics ✅
**Volume, 1RM (best lift), Frequency**
- Card 1: Weekly Volume (total lbs + delta)
- Card 2: Best Estimated 1RM (across all exercises)
- Card 3: Training Frequency (sessions per week)

### 2. Volume Chart Style ✅
**Multi-line chart** (one line per muscle, easier to compare trends)

### 3. Strength Trends Section ❌
**SKIPPED for v1.0** - Will add in future version if users request it

### 4. Freemium Gates ❌
**No gates** - All features free in v1.0

### 5. Default Time Range ✅
**12 weeks** (optimal balance of trend visibility and relevance)

### 6. Plateau Detection ❌
**SKIPPED for v1.0** - Feature deferred to future version

---

## Final MVP Scope (Simplified)

### What We're Building
1. ✅ **Analytics tab** in bottom navigation
2. ✅ **Time range selector** (4W, 12W, 6M, 1Y, ALL - default 12W)
3. ✅ **3 Hero Cards**:
   - Weekly Volume (lbs + delta + sparkline)
   - Best Estimated 1RM (across all lifts + delta)
   - Training Frequency (sessions/week + delta + sparkline)
4. ✅ **Volume by Muscle Chart** (multi-line chart, filterable by muscle)
5. ✅ **Personal Records List** (scrollable, all PRs with date)

### What We're NOT Building (Deferred)
- ❌ Strength Trends section (per-exercise 1RM charts)
- ❌ Plateau detection warnings
- ❌ Drill-down detail screens (tap for full history)
- ❌ Freemium gates
- ❌ Progressive overload score
- ❌ Muscle balance heatmap
- ❌ Export/share functionality

---

## Screen Layout (Final)

```
┌─────────────────────────────────────┐
│  Analytics                          │  ← Header
│                                     │
│  [4W] [12W] [6M] [1Y] [ALL]         │  ← Time range chips (default: 12W)
│                                     │
│  ─── THIS WEEK ───                  │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  WEEKLY VOLUME              │   │  ← Hero Card 1
│  │  180,500 lbs                │   │  (Big number: 48sp)
│  │  ↗ +12% from last week      │   │  (Delta + trend indicator)
│  │  ▃▄▅▆▇█▇ (sparkline)        │   │  (Micro trend)
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  BEST ESTIMATED 1RM         │   │  ← Hero Card 2
│  │  385 lbs (Squat)            │   │  (Best lift across all exercises)
│  │  ↗ +5 lbs from last period  │   │  (Delta comparison)
│  │  ▅▆▇█▇▆▅ (sparkline)        │   │
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  TRAINING FREQUENCY         │   │  ← Hero Card 3
│  │  4.2x per week              │   │
│  │  ↗ +0.5 from last week      │   │
│  │  ▅▆▇█▇▆▅ (sparkline)        │   │
│  └─────────────────────────────┘   │
│                                     │
│  [Scroll for details ↓]            │
├─────────────────────────────────────┤
│  ─── VOLUME BY MUSCLE ───          │
│                                     │
│  [Multi-select chips]              │  ← Filter chips (Chest, Back, Legs, etc.)
│  [Chest] [Back] [Legs] ...         │
│                                     │
│  [Multi-line chart]                │  ← Weekly volume, one line per muscle
│  Y-axis: Volume (lbs)              │  (Color-coded by muscle)
│  X-axis: Weeks                      │
│                                     │
├─────────────────────────────────────┤
│  ─── PERSONAL RECORDS ───          │
│                                     │
│  Bench Press                        │  ← Scrollable list
│  225 lbs × 8 reps                   │
│  Nov 15, 2024                       │
│                                     │
│  Squat                              │
│  315 lbs × 5 reps                   │
│  Nov 10, 2024                       │
│  ...                                │
└─────────────────────────────────────┘
```

---

## Implementation Phases

### Phase 1: Foundation (Days 1-2)

**Goals:** Set up database queries, charting library, and basic architecture

**Tasks:**

1. **Add Analytics tab to bottom nav**
   - File: `MainActivity.kt`
   - Add Analytics icon (TrendingUp from Material Icons)
   - Update navigation graph with Analytics route
   - Update BottomNavigationBar exhaustive when clause

2. **Set up Vico charting library**
   ```kotlin
   // Add to build.gradle.kts (app module)
   dependencies {
       implementation("com.patrykandpatrick.vico:compose:1.13.1")
       implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")
       implementation("com.patrykandpatrick.vico:core:1.13.1")
   }
   ```

3. **Add timestamp index to sets table**
   - File: `AppDatabase.kt`
   - Create database migration (version N → N+1)
   - Add: `CREATE INDEX IF NOT EXISTS index_sets_timestamp ON sets(timestamp)`
   - Required for performant date-range queries

4. **Create new DAO queries** (SetDao.kt):

   ```kotlin
   // Training frequency - count distinct workout days
   @Query("""
       SELECT COUNT(DISTINCT DATE(timestamp / 1000, 'unixepoch')) as days
       FROM sets
       WHERE timestamp BETWEEN :startDate AND :endDate
   """)
   suspend fun getTrainingDaysCount(startDate: Long, endDate: Long): Int

   // Volume by muscle and week
   @Query("""
       SELECT
           e.targetMuscle as muscle,
           strftime('%Y-%W', datetime(s.timestamp / 1000, 'unixepoch')) as week,
           SUM(s.weight * s.reps) as volume
       FROM sets s
       INNER JOIN exercises e ON s.exerciseId = e.id
       WHERE s.weight IS NOT NULL
         AND s.reps IS NOT NULL
         AND s.isWarmup = 0
         AND s.timestamp BETWEEN :startDate AND :endDate
       GROUP BY e.targetMuscle, week
       ORDER BY week ASC
   """)
   suspend fun getVolumeByMuscleAndWeek(
       startDate: Long,
       endDate: Long
   ): List<MuscleVolumeData>

   // Total volume for a time period (for hero card)
   @Query("""
       SELECT SUM(s.weight * s.reps) as totalVolume
       FROM sets s
       WHERE s.weight IS NOT NULL
         AND s.reps IS NOT NULL
         AND s.isWarmup = 0
         AND s.timestamp BETWEEN :startDate AND :endDate
   """)
   suspend fun getTotalVolume(startDate: Long, endDate: Long): Float?

   // Best set for 1RM calculation (across all exercises)
   @Query("""
       SELECT * FROM sets
       WHERE weight IS NOT NULL
         AND reps IS NOT NULL
         AND isWarmup = 0
         AND timestamp BETWEEN :startDate AND :endDate
       ORDER BY weight DESC
       LIMIT 50
   """)
   suspend fun getTopSetsForE1RM(startDate: Long, endDate: Long): List<Set>

   // Enhanced personal records
   @Query("""
       SELECT
           e.id as exerciseId,
           e.name as exerciseName,
           s.weight,
           s.reps,
           s.timestamp
       FROM exercises e
       INNER JOIN (
           SELECT exerciseId, MAX(weight) as maxWeight
           FROM sets
           WHERE weight IS NOT NULL AND isWarmup = 0
           GROUP BY exerciseId
       ) max_sets ON e.id = max_sets.exerciseId
       INNER JOIN sets s ON s.exerciseId = e.id AND s.weight = max_sets.maxWeight
       WHERE s.isWarmup = 0
       ORDER BY s.timestamp DESC
   """)
   suspend fun getAllPersonalRecords(): List<PersonalRecordData>
   ```

5. **Create data classes** for query results:

   ```kotlin
   data class MuscleVolumeData(
       val muscle: String,
       val week: String,
       val volume: Float
   )

   data class PersonalRecordData(
       val exerciseId: Long,
       val exerciseName: String,
       val weight: Float,
       val reps: Int,
       val timestamp: Date
   )

   data class FrequencyData(
       val sessionsPerWeek: Float,
       val delta: Float,
       val sparklineData: List<Float>
   )

   data class VolumeData(
       val totalVolume: Float,
       val percentChange: Float,
       val sparklineData: List<Float>
   )

   data class BestE1RMData(
       val exerciseName: String,
       val estimatedMax: Float,
       val delta: Float,
       val sparklineData: List<Float>
   )
   ```

6. **Create AnalyticsViewModel**

   ```kotlin
   @HiltViewModel
   class AnalyticsViewModel @Inject constructor(
       private val setDao: SetDao,
       private val exerciseDao: ExerciseDao
   ) : ViewModel() {

       private val _selectedTimeRange = MutableStateFlow(TimeRange.TWELVE_WEEKS)
       val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

       private val _frequencyData = MutableStateFlow<FrequencyData?>(null)
       val frequencyData: StateFlow<FrequencyData?> = _frequencyData.asStateFlow()

       private val _volumeData = MutableStateFlow<VolumeData?>(null)
       val volumeData: StateFlow<VolumeData?> = _volumeData.asStateFlow()

       private val _bestE1RMData = MutableStateFlow<BestE1RMData?>(null)
       val bestE1RMData: StateFlow<BestE1RMData?> = _bestE1RMData.asStateFlow()

       private val _personalRecords = MutableStateFlow<List<PersonalRecordData>>(emptyList())
       val personalRecords: StateFlow<List<PersonalRecordData>> = _personalRecords.asStateFlow()

       private val _volumeByMuscle = MutableStateFlow<Map<String, List<VolumePoint>>>(emptyMap())
       val volumeByMuscle: StateFlow<Map<String, List<VolumePoint>>> = _volumeByMuscle.asStateFlow()

       private val _selectedMuscles = MutableStateFlow(setOf("Chest", "Back", "Legs", "Shoulders", "Biceps", "Triceps", "Core", "Cardio"))
       val selectedMuscles: StateFlow<Set<String>> = _selectedMuscles.asStateFlow()

       init {
           loadAllData()
       }

       fun setTimeRange(range: TimeRange) {
           _selectedTimeRange.value = range
           loadAllData()
       }

       fun toggleMuscle(muscle: String) {
           _selectedMuscles.value = if (_selectedMuscles.value.contains(muscle)) {
               _selectedMuscles.value - muscle
           } else {
               _selectedMuscles.value + muscle
           }
       }

       private fun loadAllData() {
           viewModelScope.launch {
               val (startDate, endDate) = selectedTimeRange.value.getDateRange()
               val (prevStart, prevEnd) = selectedTimeRange.value.getPreviousPeriod()

               launch { loadTrainingFrequency(startDate, endDate, prevStart, prevEnd) }
               launch { loadVolumeData(startDate, endDate, prevStart, prevEnd) }
               launch { loadBestE1RM(startDate, endDate, prevStart, prevEnd) }
               launch { loadPersonalRecords() }
               launch { loadVolumeByMuscle(startDate, endDate) }
           }
       }

       private suspend fun loadTrainingFrequency(
           startDate: Long,
           endDate: Long,
           prevStart: Long,
           prevEnd: Long
       ) {
           val currentDays = setDao.getTrainingDaysCount(startDate, endDate)
           val previousDays = setDao.getTrainingDaysCount(prevStart, prevEnd)

           val weeks = selectedTimeRange.value.weeks.toFloat()
           val sessionsPerWeek = currentDays / weeks
           val previousSessions = previousDays / weeks
           val delta = sessionsPerWeek - previousSessions

           // Calculate sparkline (last 8 weeks)
           val sparkline = calculateFrequencySparkline(endDate)

           _frequencyData.value = FrequencyData(
               sessionsPerWeek = String.format("%.1f", sessionsPerWeek).toFloat(),
               delta = String.format("%.1f", delta).toFloat(),
               sparklineData = sparkline
           )
       }

       private suspend fun loadVolumeData(
           startDate: Long,
           endDate: Long,
           prevStart: Long,
           prevEnd: Long
       ) {
           val currentVolume = setDao.getTotalVolume(startDate, endDate) ?: 0f
           val previousVolume = setDao.getTotalVolume(prevStart, prevEnd) ?: 1f

           val percentChange = if (previousVolume > 0) {
               ((currentVolume - previousVolume) / previousVolume) * 100
           } else {
               0f
           }

           // Calculate sparkline (last 8 weeks)
           val sparkline = calculateVolumeSparkline(endDate)

           _volumeData.value = VolumeData(
               totalVolume = currentVolume,
               percentChange = String.format("%.1f", percentChange).toFloat(),
               sparklineData = sparkline
           )
       }

       private suspend fun loadBestE1RM(
           startDate: Long,
           endDate: Long,
           prevStart: Long,
           prevEnd: Long
       ) {
           val currentSets = setDao.getTopSetsForE1RM(startDate, endDate)
           val previousSets = setDao.getTopSetsForE1RM(prevStart, prevEnd)

           // Find best estimated 1RM in current period
           val bestCurrent = currentSets.maxByOrNull {
               calculateEstimated1RM(it.weight ?: 0f, it.reps ?: 1)
           }

           // Find best estimated 1RM in previous period
           val bestPrevious = previousSets.maxByOrNull {
               calculateEstimated1RM(it.weight ?: 0f, it.reps ?: 1)
           }

           if (bestCurrent != null) {
               val currentE1RM = calculateEstimated1RM(bestCurrent.weight!!, bestCurrent.reps!!)
               val previousE1RM = bestPrevious?.let {
                   calculateEstimated1RM(it.weight!!, it.reps!!)
               } ?: currentE1RM

               val delta = currentE1RM - previousE1RM

               // Get exercise name
               val exercise = exerciseDao.getExerciseById(bestCurrent.exerciseId)

               // Calculate sparkline
               val sparkline = calculateE1RMSparkline(bestCurrent.exerciseId, endDate)

               _bestE1RMData.value = BestE1RMData(
                   exerciseName = exercise?.name ?: "Unknown",
                   estimatedMax = currentE1RM,
                   delta = delta,
                   sparklineData = sparkline
               )
           }
       }

       private suspend fun loadPersonalRecords() {
           _personalRecords.value = setDao.getAllPersonalRecords()
       }

       private suspend fun loadVolumeByMuscle(startDate: Long, endDate: Long) {
           val rawData = setDao.getVolumeByMuscleAndWeek(startDate, endDate)

           // Transform to Map<Muscle, List<VolumePoint>>
           val grouped = rawData.groupBy { it.muscle }
               .mapValues { (_, data) ->
                   data.map { VolumePoint(it.week, it.volume) }
               }

           _volumeByMuscle.value = grouped
       }

       private fun calculateEstimated1RM(weight: Float, reps: Int): Float {
           // Brzycki formula: 1RM = weight / (1.0278 - 0.0278 × reps)
           if (reps == 1) return weight
           if (reps > 12) return weight // Formula breaks down above 12 reps
           return weight / (1.0278f - 0.0278f * reps)
       }

       private suspend fun calculateFrequencySparkline(endDate: Long): List<Float> {
           val sparklineData = mutableListOf<Float>()
           val oneWeek = 7 * 24 * 60 * 60 * 1000L

           for (i in 7 downTo 0) {
               val weekEnd = endDate - (i * oneWeek)
               val weekStart = weekEnd - oneWeek
               val days = setDao.getTrainingDaysCount(weekStart, weekEnd)
               sparklineData.add(days.toFloat())
           }

           return sparklineData
       }

       private suspend fun calculateVolumeSparkline(endDate: Long): List<Float> {
           val sparklineData = mutableListOf<Float>()
           val oneWeek = 7 * 24 * 60 * 60 * 1000L

           for (i in 7 downTo 0) {
               val weekEnd = endDate - (i * oneWeek)
               val weekStart = weekEnd - oneWeek
               val volume = setDao.getTotalVolume(weekStart, weekEnd) ?: 0f
               sparklineData.add(volume)
           }

           return sparklineData
       }

       private suspend fun calculateE1RMSparkline(exerciseId: Long, endDate: Long): List<Float> {
           // Similar to volume sparkline but calculate best 1RM per week
           // Implementation details...
           return emptyList() // Placeholder
       }
   }

   enum class TimeRange(val weeks: Int) {
       FOUR_WEEKS(4),
       TWELVE_WEEKS(12),
       SIX_MONTHS(26),
       ONE_YEAR(52),
       ALL_TIME(-1); // Special case: no limit

       fun getDateRange(): Pair<Long, Long> {
           val endDate = System.currentTimeMillis()
           val startDate = if (this == ALL_TIME) {
               0L
           } else {
               endDate - (weeks * 7 * 24 * 60 * 60 * 1000L)
           }
           return startDate to endDate
       }

       fun getPreviousPeriod(): Pair<Long, Long> {
           if (this == ALL_TIME) return 0L to 0L

           val currentEnd = System.currentTimeMillis()
           val currentStart = currentEnd - (weeks * 7 * 24 * 60 * 60 * 1000L)

           val previousEnd = currentStart
           val previousStart = previousEnd - (weeks * 7 * 24 * 60 * 60 * 1000L)

           return previousStart to previousEnd
       }
   }

   data class VolumePoint(
       val week: String,
       val volume: Float
   )
   ```

**Files to Create/Modify:**
- `MainActivity.kt` (add Analytics route)
- `build.gradle.kts` (add Vico dependency)
- `AppDatabase.kt` (add migration)
- `SetDao.kt` (add new queries)
- `AnalyticsViewModel.kt` (new file)
- `AnalyticsDataClasses.kt` (new file)

**Estimated Time:** 8-10 hours

---

### Phase 2: Core UI (Days 2-3)

**Goals:** Build Analytics screen scaffold with hero cards and time selector

**Tasks:**

1. **Create AnalyticsScreen composable** (new file: `AnalyticsScreen.kt`)

   ```kotlin
   @Composable
   fun AnalyticsScreen(
       viewModel: AnalyticsViewModel = hiltViewModel()
   ) {
       val selectedTimeRange by viewModel.selectedTimeRange.collectAsState()
       val frequencyData by viewModel.frequencyData.collectAsState()
       val volumeData by viewModel.volumeData.collectAsState()
       val bestE1RMData by viewModel.bestE1RMData.collectAsState()
       val personalRecords by viewModel.personalRecords.collectAsState()

       Column(
           modifier = Modifier
               .fillMaxSize()
               .background(
                   brush = Brush.verticalGradient(
                       colors = listOf(
                           Color(0xFF0A1A0A), // Gradient start
                           Color(0xFF0A0A0A)  // Gradient end
                       )
                   )
               )
               .verticalScroll(rememberScrollState())
               .padding(16.dp)
       ) {
           // Header
           Text(
               text = "Analytics",
               style = MaterialTheme.typography.headlineLarge,
               fontWeight = FontWeight.Bold,
               color = Color.White
           )

           Spacer(modifier = Modifier.height(16.dp))

           // Time range selector
           TimeRangeSelector(
               selectedRange = selectedTimeRange,
               onRangeSelected = { viewModel.setTimeRange(it) }
           )

           Spacer(modifier = Modifier.height(24.dp))

           // Section header
           Text(
               text = "THIS WEEK",
               style = MaterialTheme.typography.labelLarge,
               color = Color(0xFF9CA3AF),
               letterSpacing = 1.5.sp
           )

           Spacer(modifier = Modifier.height(16.dp))

           // Hero cards
           volumeData?.let { VolumeHeroCard(it) }
           Spacer(modifier = Modifier.height(12.dp))

           bestE1RMData?.let { BestE1RMHeroCard(it) }
           Spacer(modifier = Modifier.height(12.dp))

           frequencyData?.let { FrequencyHeroCard(it) }

           Spacer(modifier = Modifier.height(32.dp))

           // Volume by muscle section
           VolumeByMuscleSection(
               volumeData = viewModel.volumeByMuscle.collectAsState().value,
               selectedMuscles = viewModel.selectedMuscles.collectAsState().value,
               onMuscleToggle = { viewModel.toggleMuscle(it) }
           )

           Spacer(modifier = Modifier.height(32.dp))

           // Personal records section
           PersonalRecordsSection(records = personalRecords)

           Spacer(modifier = Modifier.height(32.dp))
       }
   }
   ```

2. **Implement TimeRangeSelector**

   ```kotlin
   @Composable
   fun TimeRangeSelector(
       selectedRange: TimeRange,
       onRangeSelected: (TimeRange) -> Unit
   ) {
       Row(
           modifier = Modifier
               .fillMaxWidth()
               .horizontalScroll(rememberScrollState()),
           horizontalArrangement = Arrangement.spacedBy(8.dp)
       ) {
           TimeRange.values().forEach { range ->
               FilterChip(
                   selected = selectedRange == range,
                   onClick = { onRangeSelected(range) },
                   label = {
                       Text(
                           text = range.displayName(),
                           style = MaterialTheme.typography.labelMedium
                       )
                   },
                   colors = FilterChipDefaults.filterChipColors(
                       selectedContainerColor = Color(0xFFA3E635), // Lime green
                       selectedLabelColor = Color.Black,
                       containerColor = Color(0xFF0D0D0D),
                       labelColor = Color(0xFFA3E635)
                   )
               )
           }
       }
   }

   fun TimeRange.displayName(): String = when (this) {
       TimeRange.FOUR_WEEKS -> "4W"
       TimeRange.TWELVE_WEEKS -> "12W"
       TimeRange.SIX_MONTHS -> "6M"
       TimeRange.ONE_YEAR -> "1Y"
       TimeRange.ALL_TIME -> "ALL"
   }
   ```

3. **Implement Hero Cards**

   ```kotlin
   @Composable
   fun VolumeHeroCard(data: VolumeData) {
       GlowCard(
           modifier = Modifier
               .fillMaxWidth()
               .height(160.dp)
       ) {
           Column(
               modifier = Modifier.padding(20.dp),
               verticalArrangement = Arrangement.spacedBy(8.dp)
           ) {
               // Label
               Text(
                   text = "WEEKLY VOLUME",
                   style = MaterialTheme.typography.labelLarge,
                   color = Color(0xFF9CA3AF),
                   letterSpacing = 1.5.sp
               )

               // Big number
               Text(
                   text = "${data.totalVolume.toInt().formatWithCommas()} lbs",
                   style = MaterialTheme.typography.displayLarge.copy(
                       fontSize = 48.sp,
                       fontWeight = FontWeight.Bold
                   ),
                   color = Color.White
               )

               // Delta
               Row(verticalAlignment = Alignment.CenterVertically) {
                   Icon(
                       imageVector = if (data.percentChange >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                       contentDescription = null,
                       tint = if (data.percentChange >= 0) Color(0xFFA3E635) else Color(0xFFE74C3C),
                       modifier = Modifier.size(16.dp)
                   )
                   Spacer(modifier = Modifier.width(4.dp))
                   Text(
                       text = "${if (data.percentChange >= 0) "+" else ""}${data.percentChange.toInt()}% from last period",
                       style = MaterialTheme.typography.bodyMedium,
                       color = if (data.percentChange >= 0) Color(0xFFA3E635) else Color(0xFFE74C3C)
                   )
               }

               // Sparkline
               Spacer(modifier = Modifier.weight(1f))
               if (data.sparklineData.isNotEmpty()) {
                   SparklineChart(data.sparklineData)
               }
           }
       }
   }

   @Composable
   fun BestE1RMHeroCard(data: BestE1RMData) {
       GlowCard(
           modifier = Modifier
               .fillMaxWidth()
               .height(160.dp)
       ) {
           Column(
               modifier = Modifier.padding(20.dp),
               verticalArrangement = Arrangement.spacedBy(8.dp)
           ) {
               Text(
                   text = "BEST ESTIMATED 1RM",
                   style = MaterialTheme.typography.labelLarge,
                   color = Color(0xFF9CA3AF),
                   letterSpacing = 1.5.sp
               )

               // Main value with exercise name
               Column {
                   Text(
                       text = "${data.estimatedMax.toInt()} lbs",
                       style = MaterialTheme.typography.displayLarge.copy(
                           fontSize = 48.sp,
                           fontWeight = FontWeight.Bold
                       ),
                       color = Color.White
                   )
                   Text(
                       text = data.exerciseName,
                       style = MaterialTheme.typography.bodySmall,
                       color = Color(0xFFE0E0E0)
                   )
               }

               // Delta
               Row(verticalAlignment = Alignment.CenterVertically) {
                   Icon(
                       imageVector = if (data.delta >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                       contentDescription = null,
                       tint = if (data.delta >= 0) Color(0xFFA3E635) else Color(0xFFE74C3C),
                       modifier = Modifier.size(16.dp)
                   )
                   Spacer(modifier = Modifier.width(4.dp))
                   Text(
                       text = "${if (data.delta >= 0) "+" else ""}${data.delta.toInt()} lbs from last period",
                       style = MaterialTheme.typography.bodyMedium,
                       color = if (data.delta >= 0) Color(0xFFA3E635) else Color(0xFFE74C3C)
                   )
               }

               Spacer(modifier = Modifier.weight(1f))
               if (data.sparklineData.isNotEmpty()) {
                   SparklineChart(data.sparklineData)
               }
           }
       }
   }

   @Composable
   fun FrequencyHeroCard(data: FrequencyData) {
       GlowCard(
           modifier = Modifier
               .fillMaxWidth()
               .height(160.dp)
       ) {
           Column(
               modifier = Modifier.padding(20.dp),
               verticalArrangement = Arrangement.spacedBy(8.dp)
           ) {
               Text(
                   text = "TRAINING FREQUENCY",
                   style = MaterialTheme.typography.labelLarge,
                   color = Color(0xFF9CA3AF),
                   letterSpacing = 1.5.sp
               )

               Text(
                   text = "${data.sessionsPerWeek}x per week",
                   style = MaterialTheme.typography.displayLarge.copy(
                       fontSize = 48.sp,
                       fontWeight = FontWeight.Bold
                   ),
                   color = Color.White
               )

               Row(verticalAlignment = Alignment.CenterVertically) {
                   Icon(
                       imageVector = if (data.delta >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                       contentDescription = null,
                       tint = if (data.delta >= 0) Color(0xFFA3E635) else Color(0xFFE74C3C),
                       modifier = Modifier.size(16.dp)
                   )
                   Spacer(modifier = Modifier.width(4.dp))
                   Text(
                       text = "${if (data.delta >= 0) "+" else ""}${data.delta} from last period",
                       style = MaterialTheme.typography.bodyMedium,
                       color = if (data.delta >= 0) Color(0xFFA3E635) else Color(0xFFE74C3C)
                   )
               }

               Spacer(modifier = Modifier.weight(1f))
               if (data.sparklineData.isNotEmpty()) {
                   SparklineChart(data.sparklineData)
               }
           }
       }
   }

   // Helper function
   fun Int.formatWithCommas(): String {
       return String.format("%,d", this)
   }
   ```

4. **Implement simple Sparkline**

   ```kotlin
   @Composable
   fun SparklineChart(dataPoints: List<Float>) {
       Canvas(
           modifier = Modifier
               .fillMaxWidth()
               .height(40.dp)
       ) {
           if (dataPoints.size < 2) return@Canvas

           val max = dataPoints.maxOrNull() ?: 1f
           val min = dataPoints.minOrNull() ?: 0f
           val range = if (max - min > 0) max - min else 1f

           val path = Path()
           val xStep = size.width / (dataPoints.size - 1)

           dataPoints.forEachIndexed { index, value ->
               val x = index * xStep
               val y = size.height - ((value - min) / range * size.height)

               if (index == 0) {
                   path.moveTo(x, y)
               } else {
                   path.lineTo(x, y)
               }
           }

           drawPath(
               path = path,
               color = Color(0xFFA3E635), // Lime green
               style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
           )
       }
   }
   ```

**Files to Create:**
- `AnalyticsScreen.kt`
- `AnalyticsComponents.kt` (hero cards, sparkline, etc.)

**Estimated Time:** 8-10 hours

---

### Phase 3: Volume Chart & PR List (Days 3-4)

**Goals:** Add volume by muscle chart and personal records list

**Tasks:**

1. **Implement Volume by Muscle Section**

   ```kotlin
   @Composable
   fun VolumeByMuscleSection(
       volumeData: Map<String, List<VolumePoint>>,
       selectedMuscles: Set<String>,
       onMuscleToggle: (String) -> Unit
   ) {
       Column {
           Text(
               text = "VOLUME BY MUSCLE",
               style = MaterialTheme.typography.labelLarge,
               color = Color(0xFF9CA3AF),
               letterSpacing = 1.5.sp
           )

           Spacer(modifier = Modifier.height(16.dp))

           // Muscle filter chips
           MuscleFilterChips(
               selectedMuscles = selectedMuscles,
               onMuscleToggle = onMuscleToggle
           )

           Spacer(modifier = Modifier.height(16.dp))

           // Chart
           GlowCard(
               modifier = Modifier
                   .fillMaxWidth()
                   .height(300.dp)
           ) {
               if (volumeData.isEmpty() || selectedMuscles.isEmpty()) {
                   EmptyStateMessage("No volume data available")
               } else {
                   val filteredData = volumeData.filterKeys { selectedMuscles.contains(it) }
                   VicoMultiLineChart(data = filteredData)
               }
           }
       }
   }

   @Composable
   fun MuscleFilterChips(
       selectedMuscles: Set<String>,
       onMuscleToggle: (String) -> Unit
   ) {
       val muscles = listOf("Chest", "Back", "Legs", "Shoulders", "Biceps", "Triceps", "Core", "Cardio")

       // Use FlowRow for wrapping chips
       Row(
           modifier = Modifier
               .fillMaxWidth()
               .horizontalScroll(rememberScrollState()),
           horizontalArrangement = Arrangement.spacedBy(8.dp)
       ) {
           muscles.forEach { muscle ->
               FilterChip(
                   selected = selectedMuscles.contains(muscle),
                   onClick = { onMuscleToggle(muscle) },
                   label = { Text(muscle) },
                   colors = FilterChipDefaults.filterChipColors(
                       selectedContainerColor = Color(0xFFA3E635),
                       selectedLabelColor = Color.Black,
                       containerColor = Color(0xFF0D0D0D),
                       labelColor = Color(0xFFA3E635)
                   )
               )
           }
       }
   }

   @Composable
   fun EmptyStateMessage(message: String) {
       Box(
           modifier = Modifier.fillMaxSize(),
           contentAlignment = Alignment.Center
       ) {
           Text(
               text = message,
               style = MaterialTheme.typography.bodyMedium,
               color = Color(0xFF9CA3AF)
           )
       }
   }
   ```

2. **Implement Vico Multi-Line Chart**

   ```kotlin
   @Composable
   fun VicoMultiLineChart(data: Map<String, List<VolumePoint>>) {
       // Vico chart implementation for multiple lines
       // Each muscle group gets its own line with a distinct color

       val muscleColors = mapOf(
           "Chest" to Color(0xFFEF4444),
           "Back" to Color(0xFF3B82F6),
           "Legs" to Color(0xFF10B981),
           "Shoulders" to Color(0xFFF59E0B),
           "Biceps" to Color(0xFF8B5CF6),
           "Triceps" to Color(0xFFEC4899),
           "Core" to Color(0xFF14B8A6),
           "Cardio" to Color(0xFF6B7280)
       )

       // Transform data for Vico
       // Note: Detailed Vico implementation will depend on the specific API
       // This is a placeholder structure

       Box(
           modifier = Modifier
               .fillMaxSize()
               .padding(16.dp)
       ) {
           // Vico Chart implementation
           // See Vico documentation for multi-line chart setup
           // Key: Each series needs color mapping, data transformation, legend

           Text(
               text = "Multi-line chart implementation with Vico",
               color = Color.White,
               modifier = Modifier.align(Alignment.Center)
           )
       }
   }
   ```

3. **Implement Personal Records Section**

   ```kotlin
   @Composable
   fun PersonalRecordsSection(records: List<PersonalRecordData>) {
       Column {
           Text(
               text = "PERSONAL RECORDS",
               style = MaterialTheme.typography.labelLarge,
               color = Color(0xFF9CA3AF),
               letterSpacing = 1.5.sp
           )

           Spacer(modifier = Modifier.height(16.dp))

           if (records.isEmpty()) {
               GlowCard(
                   modifier = Modifier
                       .fillMaxWidth()
                       .height(120.dp)
               ) {
                   EmptyStateMessage("No personal records yet. Start logging workouts!")
               }
           } else {
               records.forEach { record ->
                   PersonalRecordItem(record)
                   Spacer(modifier = Modifier.height(8.dp))
               }
           }
       }
   }

   @Composable
   fun PersonalRecordItem(record: PersonalRecordData) {
       GlowCard(
           modifier = Modifier.fillMaxWidth()
       ) {
           Row(
               modifier = Modifier
                   .fillMaxWidth()
                   .padding(16.dp),
               horizontalArrangement = Arrangement.SpaceBetween,
               verticalAlignment = Alignment.CenterVertically
           ) {
               Column(modifier = Modifier.weight(1f)) {
                   Text(
                       text = record.exerciseName,
                       style = MaterialTheme.typography.titleMedium,
                       fontWeight = FontWeight.Bold,
                       color = Color.White
                   )
                   Spacer(modifier = Modifier.height(4.dp))
                   Text(
                       text = "${record.weight.toInt()} lbs × ${record.reps} reps",
                       style = MaterialTheme.typography.bodyMedium,
                       color = Color(0xFFE0E0E0)
                   )
               }

               Text(
                   text = formatDate(record.timestamp),
                   style = MaterialTheme.typography.bodySmall,
                   color = Color(0xFF9CA3AF)
               )
           }
       }
   }

   // Helper function
   fun formatDate(date: Date): String {
       val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
       return formatter.format(date)
   }
   ```

**Files to Modify/Create:**
- `AnalyticsScreen.kt` (add sections)
- `AnalyticsComponents.kt` (add volume chart, PR list)
- Research Vico documentation for multi-line chart implementation

**Estimated Time:** 10-12 hours

---

### Phase 4: Polish & Testing (Day 5)

**Goals:** Handle edge cases, add loading states, test thoroughly

**Tasks:**

1. **Edge Case Handling**:
   - Empty state (no workouts logged)
   - Single data point (can't draw line chart)
   - Very high/low values (chart scaling)
   - Zero volume/frequency (divide by zero protection)
   - Missing exercises (deleted exercise with historical data)

2. **Loading States**:
   ```kotlin
   @Composable
   fun LoadingHeroCard() {
       GlowCard(
           modifier = Modifier
               .fillMaxWidth()
               .height(160.dp)
       ) {
           Box(
               modifier = Modifier.fillMaxSize(),
               contentAlignment = Alignment.Center
           ) {
               CircularProgressIndicator(
                   color = Color(0xFFA3E635),
                   modifier = Modifier.size(48.dp)
               )
           }
       }
   }
   ```

3. **Error Handling**:
   - Database query failures (catch exceptions)
   - Invalid date ranges
   - Calculation errors (safe division)

4. **Performance Testing**:
   - Test with 0 sets (empty state)
   - Test with 100 sets
   - Test with 500 sets
   - Test with 1000+ sets
   - Profile query times (should be <100ms)

5. **UI Polish**:
   - Add subtle animations for time range switching
   - Ensure consistent spacing
   - Test on different screen sizes
   - Verify dark theme consistency
   - Test scrolling behavior

**Estimated Time:** 6-8 hours

---

## Design Specifications

### Color Palette

```kotlin
object AnalyticsColors {
    val LimeGreen = Color(0xFFA3E635)        // Primary accent
    val DarkGreen = Color(0xFF84CC16)         // Pressed state
    val LightGreen = Color(0xFFBEF264)        // Highlights

    val BackgroundStart = Color(0xFF0A1A0A)   // Gradient start
    val BackgroundEnd = Color(0xFF0A0A0A)     // Gradient end
    val Surface = Color(0xFF0D0D0D)           // Card background

    val TextPrimary = Color(0xFFFFFFFF)       // Main text
    val TextSecondary = Color(0xFFE0E0E0)     // Secondary text
    val TextTertiary = Color(0xFF9CA3AF)      // Labels, hints

    val PositiveTrend = Color(0xFFA3E635)     // Up arrow
    val NegativeTrend = Color(0xFFE74C3C)     // Down arrow

    val MuscleChest = Color(0xFFEF4444)       // Red
    val MuscleBack = Color(0xFF3B82F6)        // Blue
    val MuscleLegs = Color(0xFF10B981)        // Green
    val MuscleShoulders = Color(0xFFF59E0B)   // Amber
    val MuscleBiceps = Color(0xFF8B5CF6)      // Purple
    val MuscleTriceps = Color(0xFFEC4899)     // Pink
    val MuscleCore = Color(0xFF14B8A6)        // Teal
    val MuscleCardio = Color(0xFF6B7280)      // Gray
}
```

### Typography

```kotlin
object AnalyticsTypography {
    val HeroNumber = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )

    val HeroLabel = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp,
        color = Color(0xFF9CA3AF)
    )

    val HeroDelta = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
    )

    val SectionHeader = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp,
        color = Color(0xFF9CA3AF)
    )
}
```

### Dimensions

```kotlin
object AnalyticsDimensions {
    val HeroCardHeight = 160.dp
    val ChartHeight = 300.dp
    val SparklineHeight = 40.dp

    val ChipHeight = 32.dp
    val ChipSpacing = 8.dp

    val SectionSpacing = 32.dp
    val CardSpacing = 12.dp
    val InnerPadding = 16.dp
}
```

---

## Technical Specifications

### Database Performance Requirements
- **Query timeout**: 200ms max for any single query
- **Indices required**:
  - `Index("exerciseId")` ✅ (exists)
  - `Index("workoutId")` ✅ (exists)
  - `Index("timestamp")` ⚠️ (need to add in migration)

### 1RM Calculation Formula
**Brzycki Formula**: `1RM = weight / (1.0278 - 0.0278 × reps)`

**Constraints**:
- Valid for reps: 1-12
- Returns `weight` unchanged if reps > 12 (formula unreliable)
- Returns `weight` if reps = 1 (no calculation needed)

### Delta Calculation (Comparison Logic)
**For hero cards**: Compare current period to previous period of equal length

Example (12-week view):
- **Current period**: Weeks -12 to 0 (now)
- **Previous period**: Weeks -24 to -12
- **Delta**: `(current - previous) / previous * 100` for percentage
- **Delta**: `current - previous` for absolute values

### Sparkline Data
**8 data points**: One per week for the last 8 weeks
**Calculation**: Run query for each week bucket, collect values
**Rendering**: Simple line chart using Canvas, no axes or labels

---

## Implementation Timeline

| Phase | Features | Time Estimate | Files |
|-------|----------|---------------|-------|
| **Phase 1** | Foundation (DAO, ViewModel, nav) | 8-10 hours | SetDao, ViewModel, Database, MainActivity |
| **Phase 2** | Hero Cards & Time Selector | 8-10 hours | AnalyticsScreen, Components |
| **Phase 3** | Volume Chart & PR List | 10-12 hours | AnalyticsScreen, Components, Vico |
| **Phase 4** | Polish & Testing | 6-8 hours | All files |
| **TOTAL** | **32-40 hours (4-5 days)** | - |

---

## Testing Checklist

### Functionality Tests
- [ ] Analytics tab appears in bottom nav
- [ ] Default time range is 12 weeks
- [ ] Time range selector switches correctly
- [ ] Hero cards display correct data
- [ ] Sparklines render without crashing
- [ ] Volume chart shows selected muscles only
- [ ] Muscle filter chips toggle on/off
- [ ] PR list shows all personal records
- [ ] Delta calculations are accurate (positive/negative)
- [ ] 1RM calculation matches Brzycki formula
- [ ] Empty states show when no data exists

### Edge Case Tests
- [ ] No workouts logged (all empty states)
- [ ] Only 1 workout logged (sparkline with 1 point)
- [ ] Only warmup sets logged (should show 0 volume)
- [ ] Deleted exercise with historical data (should still show in PRs)
- [ ] Extreme values (very high weights, 100+ reps)
- [ ] Date range with no data (empty chart)
- [ ] All muscles deselected (empty chart)

### Performance Tests
- [ ] Query time with 100 sets < 50ms
- [ ] Query time with 500 sets < 100ms
- [ ] Query time with 1000 sets < 150ms
- [ ] No UI lag when switching time ranges
- [ ] Smooth scrolling with all sections visible

### UI/UX Tests
- [ ] Consistent spacing throughout
- [ ] Colors match existing theme
- [ ] Text is readable on all backgrounds
- [ ] Icons are correct size and color
- [ ] Cards have consistent padding
- [ ] Charts are properly scaled
- [ ] No layout overflow issues

---

## Known Limitations & Future Enhancements

### Current Limitations
- No per-exercise 1RM trends (strength trends section skipped)
- No drill-down detail screens (tap to see full history)
- No plateau detection warnings
- No export/share functionality
- Sparklines are simple (no hover/tap for values)

### Future Enhancements (Post-MVP)
- Add per-exercise strength trend cards (Phase 3 deferred)
- Add drill-down screens for detailed analysis
- Add plateau detection with actionable suggestions
- Add progressive overload score calculation
- Add muscle balance heatmap
- Add export charts as images
- Add share functionality (text-only, no social)
- Add comparison mode (this month vs last month)

---

## Dependencies

### New Dependencies to Add
```kotlin
// build.gradle.kts (app module)
dependencies {
    // Vico charting library
    implementation("com.patrykandpatrick.vico:compose:1.13.1")
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")
    implementation("com.patrykandpatrick.vico:core:1.13.1")
}
```

### Existing Dependencies (Already in Project)
- Jetpack Compose (UI framework)
- Room (database)
- Hilt (dependency injection)
- Coroutines (async operations)
- Material3 (UI components)

---

## File Structure

```
app/src/main/java/com/example/gymtime/
├── ui/
│   └── analytics/
│       ├── AnalyticsScreen.kt          (new)
│       ├── AnalyticsViewModel.kt        (new)
│       ├── AnalyticsComponents.kt       (new)
│       └── AnalyticsDataClasses.kt      (new)
├── data/
│   └── dao/
│       └── SetDao.kt                    (modify - add queries)
│   └── database/
│       └── AppDatabase.kt               (modify - add migration)
└── MainActivity.kt                       (modify - add nav route)
```

---

## Success Criteria

### MVP is complete when:
1. ✅ Analytics tab exists in bottom nav and is accessible
2. ✅ Time range selector works (default 12W, can switch to others)
3. ✅ All 3 hero cards display correct data with sparklines
4. ✅ Volume by muscle chart renders with filterable muscle chips
5. ✅ Personal records list shows all PRs sorted by date
6. ✅ Empty states handled gracefully (no crashes)
7. ✅ Performance is acceptable (<100ms queries with 500 sets)
8. ✅ UI matches existing app theme and design language

### Definition of Done:
- Code compiles without errors
- No crashes on typical usage
- Manual testing passes all checklist items
- Performance benchmarks met
- UI reviewed and approved
- Ready to merge to main branch

---

**Document Status**: ✅ Ready for Implementation
**Last Updated**: December 1, 2025
**Next Step**: Begin Phase 1 - Foundation (DAO queries + ViewModel)
