package com.daksha.hrms.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daksha.hrms.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            _error.value = "Email and password are required"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = authRepository.login(email, pass)
            _loading.value = false
            result.onSuccess {
                onSuccess()
            }.onFailure {
                _error.value = it.message ?: "Login failed"
            }
        }
    }

    fun requestAccess(companyId: String, code: String, name: String, email: String, onSuccess: () -> Unit) {
        if (companyId.isBlank() || code.isBlank() || name.isBlank() || email.isBlank()) {
            _error.value = "All fields are required"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = authRepository.requestAccess(companyId, code, name, email)
            _loading.value = false
            result.onSuccess {
                _message.value = it
                onSuccess()
            }.onFailure {
                _error.value = it.message ?: "Failed to submit request"
            }
        }
    }

    fun forgotPassword(email: String, onSuccess: () -> Unit) {
        if (email.isBlank()) {
            _error.value = "Enter your work email"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = authRepository.forgotPassword(email)
            _loading.value = false
            result.onSuccess {
                _message.value = it
                onSuccess()
            }.onFailure {
                _error.value = it.message ?: "Failed to request reset"
            }
        }
    }

    fun changePassword(current: String, newPass: String, onSuccess: () -> Unit) {
        if (current.isBlank() || newPass.isBlank()) {
            _error.value = "Current and new password are required"
            return
        }
        if (newPass.length < 14) {
            _error.value = "New password must be at least 14 characters long"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = authRepository.changePassword(current, newPass)
            _loading.value = false
            result.onSuccess {
                _message.value = it
                onSuccess()
            }.onFailure {
                _error.value = it.message ?: "Password change failed"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearMessage() {
        _message.value = null
    }
}
