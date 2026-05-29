package com.example.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object TagsListSerializer : KSerializer<List<String>?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TagsList")
    
    override fun serialize(encoder: Encoder, value: List<String>?) {
        if (value != null) {
            encoder.encodeSerializableValue(ListSerializer(String.serializer()), value)
        } else {
            encoder.encodeNull()
        }
    }

    override fun deserialize(decoder: Decoder): List<String>? {
        val input = decoder as? JsonDecoder ?: throw IllegalStateException("Only JSON is supported")
        val element = input.decodeJsonElement()
        
        return when (element) {
            is JsonArray -> try { element.mapNotNull { it.jsonPrimitive.contentOrNull } } catch (e: Exception) { emptyList() }
            is JsonPrimitive -> {
                if (element.isString) {
                    val content = element.contentOrNull
                    if (content.isNullOrBlank()) emptyList() else listOf(content)
                } else emptyList()
            }
            else -> emptyList()
        }
    }
}

object StringListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("StringList")

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeSerializableValue(ListSerializer(String.serializer()), value)
    }

    override fun deserialize(decoder: Decoder): List<String> {
        val input = decoder as? JsonDecoder ?: throw IllegalStateException("Only JSON is supported")
        val element = input.decodeJsonElement()
        
        return when (element) {
            is JsonArray -> try { element.mapNotNull { it.jsonPrimitive.contentOrNull } } catch (e: Exception) { emptyList() }
            is JsonPrimitive -> {
                if (element.isString) {
                    val content = element.contentOrNull
                    if (content.isNullOrBlank()) emptyList() else listOf(content)
                } else emptyList()
            }
            else -> emptyList()
        }
    }
}

@Serializable
data class QuestionMetadata(
    val id: String,
    @SerialName("v1_id") val v1Id: String? = null,
    val subject: String? = null,
    val topic: String? = null,
    val subTopic: String? = null,
    val difficulty: String? = null,
    val examName: String? = null,
    @SerialName("examYear") val year: String? = null,
    @SerialName("examDateShift") val shift: String? = null,
    val questionType: String? = null,
    @Serializable(with = TagsListSerializer::class) val tags: List<String>? = emptyList()
)

@Serializable
data class QuestionPayload(
    val id: String,
    val v1_id: String? = null,
    val examName: String? = null,
    val examYear: Int? = null,
    val examDateShift: String? = null,
    val subject: String? = null,
    val topic: String? = null,
    val subTopic: String? = null,
    val sourceInfo: JsonElement? = null,
    val classification: JsonElement? = null,
    val tags: List<String> = emptyList(),
    val properties: JsonElement? = null,
    @SerialName("question") val questionText: String = "",
    val question_hi: String? = null,
    @Serializable(with = StringListSerializer::class) val options: List<String> = emptyList(),
    @Serializable(with = TagsListSerializer::class) val options_hi: List<String>? = null,
    @SerialName("correct") val correctOption: String = "",
    val explanation: JsonElement? = null,
    val ask_ai_explanation: JsonElement? = null
)
