package com.example.telegrambackup.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.telegrambackup.data.AuthRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isOtpSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Forgot Password State
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var forgotOtp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var forgotMessage by remember { mutableStateOf<String?>(null) }
    var isForgotLoading by remember { mutableStateOf(false) }

    // Forgot Password OTP Dialog
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LockReset, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Password via OTP")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your email address to receive a 6-digit OTP code:", style = MaterialTheme.typography.bodySmall)
                    
                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = { forgotEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = forgotOtp,
                            onValueChange = { forgotOtp = it },
                            label = { Text("6-Digit OTP") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (forgotEmail.contains("@")) {
                                    isForgotLoading = true
                                    scope.launch {
                                        val res = authRepository.requestOtp(forgotEmail, "reset")
                                        isForgotLoading = false
                                        res.onSuccess { forgotMessage = "OTP sent successfully! Please check your Email Inbox or Spam folder." }.onFailure { forgotMessage = it.message }
                                    }
                                } else {
                                    forgotMessage = "Enter a valid email."
                                }
                            },
                            enabled = !isForgotLoading
                        ) {
                            Text("Send OTP")
                        }
                    }

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    forgotMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (forgotOtp.trim().length >= 4 && newPassword.trim().length >= 4) {
                            isForgotLoading = true
                            scope.launch {
                                val res = authRepository.resetPassword(forgotEmail.trim(), forgotOtp.trim(), newPassword.trim())
                                isForgotLoading = false
                                res.onSuccess {
                                    showForgotPasswordDialog = false
                                    successMessage = "Password reset successfully! Log in with your new password."
                                }.onFailure {
                                    forgotMessage = it.message
                                }
                            }
                        } else {
                            forgotMessage = "Fill in OTP and New Password."
                        }
                    },
                    enabled = !isForgotLoading
                ) {
                    Text("Update Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Lock",
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (isRegisterMode) "Create Account" else "Welcome Back",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = if (isRegisterMode) "Verify email OTP to protect your backup server" else "Sign in to access your backed-up files",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        errorMessage?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        successMessage?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = { Text("Email Address") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        // OTP Request / Verification Section in Register Mode
        AnimatedVisibility(visible = isRegisterMode) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it; errorMessage = null },
                        label = { Text("6-Digit OTP") },
                        leadingIcon = { Icon(Icons.Default.MarkEmailRead, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (email.contains("@")) {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val res = authRepository.requestOtp(email, "register")
                                    isLoading = false
                                    res.onSuccess {
                                        isOtpSent = true
                                        successMessage = "OTP sent successfully! Please check your Email Inbox or Spam folder."
                                    }.onFailure {
                                        errorMessage = it.message
                                    }
                                }
                            } else {
                                errorMessage = "Please enter a valid email address first."
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Text(if (isOtpSent) "Resend" else "Get OTP")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        if (!isRegisterMode) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    forgotEmail = email
                    showForgotPasswordDialog = true
                }) {
                    Text("Forgot Password?", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        AnimatedVisibility(visible = isRegisterMode) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMessage = null },
                    label = { Text("Confirm Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                errorMessage = null
                if (isRegisterMode) {
                    if (otpCode.length < 4) {
                        errorMessage = "Please enter the 6-digit OTP code sent to your email."
                        return@Button
                    }
                    if (password != confirmPassword) {
                        errorMessage = "Passwords do not match."
                        return@Button
                    }
                    isLoading = true
                    scope.launch {
                        val res = authRepository.registerWithOtp(email, otpCode, password)
                        isLoading = false
                        res.onSuccess {
                            successMessage = it
                            onAuthSuccess()
                        }.onFailure {
                            errorMessage = it.message
                        }
                    }
                } else {
                    isLoading = true
                    scope.launch {
                        val res = authRepository.login(email, password)
                        isLoading = false
                        res.onSuccess {
                            successMessage = it
                            onAuthSuccess()
                        }.onFailure {
                            errorMessage = it.message
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isRegisterMode) "Verify OTP & Register" else "Sign In")
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = {
                isRegisterMode = !isRegisterMode
                errorMessage = null
                successMessage = null
            }
        ) {
            Text(
                if (isRegisterMode) "Already have an account? Sign In" else "Don't have an account? Register with Email OTP"
            )
        }
    }
}
