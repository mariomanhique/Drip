package com.mariomanhique.drip.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedButton
import com.mariomanhique.drip.data.Seed
import com.mariomanhique.drip.ui.DripLime
import com.mariomanhique.drip.ui.DripViewModel

@Composable
fun AuthScreen(vm: DripViewModel) {
    var registering by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var ageOk by rememberSaveable { mutableStateOf(false) }
    val busy = vm.authBusy

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("DRIP", fontSize = 48.sp, fontWeight = FontWeight.Black, color = DripLime)
        Text(
            "Unleash your style potential, drip by drip.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (registering) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (registering) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(ageOk, { ageOk = it })
                Text("I am ${com.mariomanhique.drip.domain.Rules.MIN_AGE} or older", fontSize = 14.sp)
            }
        }
        vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
        Button(
            enabled = !busy,
            onClick = {
                vm.consumeMessage()
                when {
                    registering -> vm.signUpEmail(name, email, password, ageOk)
                    // Seeded demo accounts have no email; they still sign in by handle.
                    '@' !in email -> vm.login(email, password)
                    else -> vm.signInEmail(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Please wait…" else if (registering) "Create account" else "Sign in") }
        OutlinedButton(
            enabled = !busy,
            onClick = { vm.consumeMessage(); vm.signInGoogle(registering, ageOk) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue with Google") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (registering) "Have an account?" else "New to Drip?")
            TextButton({ registering = !registering }) { Text(if (registering) "Sign in" else "Register") }
        }
        Text(
            "Demo: type a handle (ama, rui, ines, joao, marta, pedro… or u1–u20) as the email / ${Seed.DEMO_PASSWORD}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}
