/*
 * Copyright (c) 2017 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sweers.catchup.data.smmry.model

import com.google.auto.value.AutoValue
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.sweers.catchup.data.adapters.UnEscape
import java.io.IOException
import java.lang.ref.WeakReference
import java.lang.reflect.Type

sealed class SmmryResponse

// 0 - Internal server problem which isn't your fault
data class InternalError(val message: String) : SmmryResponse()

// 1 - Incorrect submission variables
data class IncorrectVariables(val message: String) : SmmryResponse()

// 2 - Intentional restriction (low credits/disabled API key/banned API key)
data class ApiRejection(val message: String) : SmmryResponse()

// 3 - Summarization error
data class SummarizationError(val message: String) : SmmryResponse()

object UnknownErrorCode : SmmryResponse()

@AutoValue
abstract class Success : SmmryResponse() {

  /**
   * Contains the amount of characters returned
   */
  @Json(name = "sm_api_character_count") abstract fun characterCount(): String

  /**
   * Contains the title when available
   */
  @Json(name = "sm_api_title") @UnEscape abstract fun title(): String

  /**
   * Contains the summary
   */
  @Json(name = "sm_api_content") abstract fun content(): String

  /**
   * Contains top ranked keywords in descending order
   */
  @Json(name = "sm_api_keyword_array") abstract fun keywords(): List<String>?

  companion object {

    @JvmStatic
    fun jsonAdapter(moshi: Moshi): JsonAdapter<Success> {
      return AutoValue_Success.MoshiJsonAdapter(moshi)
    }
  }
}

class SmmryResponseFactory : JsonAdapter.Factory {

  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
    val clazz = Types.getRawType(type)
    if (SmmryResponse::class.java != clazz) {
      return null
    }
    return object : JsonAdapter<Any>() {
      @Throws(IOException::class)
      override fun fromJson(reader: JsonReader): Any? {
        val jsonValue = reader.readJsonValue()

        @Suppress("UNCHECKED_CAST")
        val value = jsonValue as Map<String, Any>
        value["sm_api_error"]?.let {
          val code = (it as Double).toInt()
          val message = (value["sm_api_message"] as String).toLowerCase().capitalize()
          return when (code) {
            0 -> InternalError(message)
            1 -> IncorrectVariables(message)
            2 -> ApiRejection(message)
            3 -> SummarizationError(message)
            else -> UnknownErrorCode
          }
        }
        return moshi.adapter(Success::class.java).fromJsonValue(value)
      }

      @Throws(IOException::class)
      override fun toJson(writer: JsonWriter, value: Any?) {
        TODO("Unsupported")
      }
    }
  }

  companion object {
    private var instance: WeakReference<SmmryResponseFactory>? = null

    fun getInstance(): SmmryResponseFactory {
      return instance?.get() ?: SmmryResponseFactory().also { instance = WeakReference(it) }
    }
  }
}
