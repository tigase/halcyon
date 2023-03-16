package accountregistration

import tigase.halcyon.core.builder.createHalcyon

fun main() {
	val halcyon = createHalcyon {
		register {
			this.domain = "sure.im"
			this.registrationFormHandler {
				it.getFieldByVar("username")!!.fieldValue = "myusername"
				it.getFieldByVar("password")!!.fieldValue = "mysecretpassword"
				it.getFieldByVar("email")!!.fieldValue = "myusername@mailserver.com"
				it.getFieldByVar("captcha")!!.fieldValue = "999"
			}
			this.registrationHandler { it }
		}
	}
	halcyon.connectAndWait()
	halcyon.disconnect()
}