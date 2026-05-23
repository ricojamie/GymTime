package com.example.gymtime.ui.report

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.domain.report.MonthlyReport
import com.example.gymtime.domain.report.MonthlyReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MonthlyReportViewModel @Inject constructor(
    private val monthlyReportUseCase: MonthlyReportUseCase
) : ViewModel() {

    private val _report = MutableStateFlow<MonthlyReport?>(null)
    val report: StateFlow<MonthlyReport?> = _report.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _report.value = monthlyReportUseCase()
            } catch (e: Exception) {
                Log.e("MonthlyReportVM", "Error loading monthly report", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
