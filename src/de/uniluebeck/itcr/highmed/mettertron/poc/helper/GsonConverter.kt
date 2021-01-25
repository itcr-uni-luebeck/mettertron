package de.uniluebeck.itcr.highmed.mettertron.poc.helper

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import de.uniluebeck.itcr.highmed.mettertron.poc.api.mdr.MdrApiAttributeValue
import java.lang.reflect.Type

class GsonConverter {

    class MdrApiAttributeDeserializer : JsonDeserializer<MdrApiAttributeValue> {
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): MdrApiAttributeValue {
            with(json) {
                if (this == null)
                    throw JsonParseException("an attribute value was null!")
                if (this.isJsonPrimitive)
                    return MdrApiAttributeValue.MdrApiAttributeStringValue(this.asString)
                else if (this.isJsonArray) {
                    return MdrApiAttributeValue.MdrApiAttributeMultiValue(
                        this.asJsonArray.map { it.asString }
                    )
                }
            }
            throw JsonParseException("MdrApiAttributeValue could not be deserialised")
        }
    }
}