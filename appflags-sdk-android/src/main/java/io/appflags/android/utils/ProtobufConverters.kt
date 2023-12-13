package io.appflags.android.utils

import io.appflags.android.exception.AppFlagsException
import io.appflags.android.models.AppFlagsFlag
import io.appflags.android.models.AppFlagsUser
import io.appflags.android.models.Configuration
import io.appflags.protos.ComputedFlag
import io.appflags.protos.FlagValueType
import io.appflags.protos.GetFlagsResponse
import io.appflags.protos.User

class ProtobufConverters private constructor() {
    companion object {
        fun toUserProto(user: AppFlagsUser): User {
            val proto = User.newBuilder()
                .setKey(user.key)
                .build();
            return proto;
        }

        fun toConfiguration(getFlagsResponse: GetFlagsResponse): Configuration {
            val flags = mutableMapOf<String, ComputedFlag>()
            for (flag in getFlagsResponse.flagsList) {
                flags[flag.key] = flag
            }
            return Configuration(flags)
        }

        fun toAppFlagsFlag(proto: ComputedFlag): AppFlagsFlag<Any> {
            when (proto.valueType) {
                FlagValueType.BOOLEAN -> {
                    return AppFlagsFlag(
                        proto.key,
                        fromFlagValueType(proto.valueType),
                        proto.value.booleanValue
                    )
                }
                FlagValueType.DOUBLE -> {
                    return AppFlagsFlag(
                        proto.key,
                        fromFlagValueType(proto.valueType),
                        proto.value.doubleValue
                    )
                }
                FlagValueType.STRING -> {
                    return AppFlagsFlag(
                        proto.key,
                        fromFlagValueType(proto.valueType),
                        proto.value.stringValue
                    )
                }
                else -> {
                    throw AppFlagsException("Unexpected FlagValueType ${proto.valueType.name}")
                }
            }
        }

        private fun fromFlagValueType(valueType: FlagValueType): AppFlagsFlag.FlagType {
            return when (valueType) {
                FlagValueType.BOOLEAN -> AppFlagsFlag.FlagType.BOOLEAN
                FlagValueType.DOUBLE -> AppFlagsFlag.FlagType.NUMBER
                FlagValueType.STRING -> AppFlagsFlag.FlagType.STRING
                else -> throw AppFlagsException("Unexpected FlagValueType ${valueType.name}")
            }
        }
    }
}
