/*
 * Tigase Halcyon XMPP Library
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.halcyon.core

import tigase.halcyon.core.requests.IQResult
import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.requests.StanzaResult
import tigase.halcyon.core.xmpp.ErrorCondition

typealias AsyncResult<V,F> = (Result<V,F>) -> Unit

sealed class Result<out V, out F> {
    fun <X>map(fn: (V)->X): Result<X,F> {
        when (this) {
            is Success -> return Success(fn(this.value));
            is Failure -> return Failure(this.failure);
        }
    }

    fun <X>mapError(fn: (F)->X): Result<V,X> {
        when (this) {
            is Success -> return Success(this.value);
            is Failure -> return Failure(fn(this.failure));
        }
    }

    class Success<out V,out F>(val value: V): Result<V, F>() {}
    class Failure<out V, out F>(val failure: F): Result<V,F>() {}
}

public fun <V,X> IQResult<V>.mapToResult(fn: (V?)->X): Result<X, ErrorCondition> {
    when (this) {
        is IQResult.Success -> return Result.Success(fn(get()));
        is IQResult.Error -> return Result.Failure(this.error);
    }
}

fun <T: Request<*, *>> StanzaResult<T>.mapToResult(): Result<Unit, ErrorCondition> {
    when (this) {
        is StanzaResult.Sent -> return Result.Success(Unit);
        is StanzaResult.NotSent -> return Result.Failure(ErrorCondition.ServiceUnavailable);
    }
}