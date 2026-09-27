package com.maksimowiczm.foodyou.training

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.*

/** Uses the existing project's public client configuration; no new account or Firebase project. */
internal class FirebaseTrainingRemote internal constructor(private val app: FirebaseApp?) : TrainingRemote {
    private val auth = app?.let(FirebaseAuth::getInstance)
    private val firestore = app?.let { FirebaseFirestore.getInstance(it, "(default)").apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
    } }
    override val configured = app != null
    private fun currentAccount() = auth?.currentUser?.let { TrainingAccount(uid = it.uid, email = it.email) }
    private val accounts = MutableStateFlow(currentAccount())
    override val account = accounts.asStateFlow()
    private val listener = FirebaseAuth.AuthStateListener { accounts.value = currentAccount() }
    init { auth?.addAuthStateListener(listener) }

    override suspend fun signIn(email: String, password: String) {
        requireNotNull(auth).signInWithEmailAndPassword(email, password).await()
        accounts.value = currentAccount()
    }
    override fun signOut() { auth?.signOut(); accounts.value = null }

    override suspend fun fetch(account: TrainingAccount): List<TrainingDocument> {
        check(account.project == TRAINING_PROJECT && account.uid == auth?.currentUser?.uid)
        val snapshot = requireNotNull(firestore).collection("users").document(account.uid)
            .collection("trainingSessions").get(Source.SERVER).await()
        return snapshot.documents.map { document ->
            val fields = buildJsonObject {
                for ((key, value) in document.data.orEmpty()) {
                    if (key != "receivedAt") put(key, when (value) {
                        is String -> JsonPrimitive(value)
                        is Long -> JsonPrimitive(value)
                        is Double -> JsonPrimitive(value)
                        is Boolean -> JsonPrimitive(value)
                        else -> JsonNull
                    })
                }
            }
            val receivedAt = (document.data?.get("receivedAt") as? Timestamp)?.let {
                Instant.fromEpochSeconds(it.seconds, it.nanoseconds)
            }
            TrainingDocument(document.id, fields, receivedAt)
        }
    }

    companion object {
        fun create(context: Context): FirebaseTrainingRemote {
            val app = try {
                val config = context.assets.open("training-firebase.json").bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
                check(config.getValue("projectId").jsonPrimitive.content == TRAINING_PROJECT)
                val options = FirebaseOptions.Builder()
                    .setProjectId(TRAINING_PROJECT)
                    .setApiKey(config.getValue("apiKey").jsonPrimitive.content)
                    .setApplicationId(config.getValue("appId").jsonPrimitive.content)
                    .build()
                FirebaseApp.getApps(context).firstOrNull { it.name == "training-import" }
                    ?: FirebaseApp.initializeApp(context, options, "training-import")
            } catch (_: Exception) { null }
            return FirebaseTrainingRemote(app)
        }
    }
}
