/*
 * halcyon-core
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
package tigase.halcyon.core.xmpp.modules.vcard

import tigase.halcyon.core.xml.element

@DslMarker
annotation class VCardTagMarker

@VCardTagMarker
class VCB(val vcard: VCard) {

	//	var addresses: List<Address> by VCardElementsList(path = arrayOf("adr"), factory = ::Address)
	var birthday: String?
		set(value) {
			vcard.birthday = value
		}
		get() = vcard.birthday

	//	var emails: List<Email> by VCardElementsList(path = arrayOf("email"), factory = ::Email)
	var formattedName: String?
		set(value) {
			vcard.formattedName = value
		}
		get() = vcard.formattedName
	var nickname: String?
		set(value) {
			vcard.nickname = value
		}
		get() = vcard.nickname

	//	var organizations: List<Organization> by VCardElementsList(path = arrayOf("org"), factory = ::Organization)
//	var photos: List<Photo> by VCardElementsList(path = arrayOf("photo"), factory = Photo.Companion::create)
	var role: String?
		set(value) {
			vcard.role = value
		}
		get() = vcard.role

	//	var structuredName: StructuredName? by VCardElement(path = arrayOf("n"), factory = ::StructuredName)
//	var telephones: List<Telephone> by VCardElementsList(path = arrayOf("tel"), factory = ::Telephone)
	var timeZone: String?
		set(value) {
			vcard.timeZone = value
		}
		get() = vcard.timeZone

	@VCardTagMarker
	class ParametersBldr(private val params: Parameters) {

		var pref: Int?
			set(value) {
				params.pref = value
			}
			get() = params.pref

		operator fun String.unaryPlus() {
			params.types = params.types + listOf(this)
		}

	}

	@VCardTagMarker
	class AddressBldr(private val addr: Address) {

		var street: String?
			set(value) {
				addr.street = value
			}
			get() = addr.street
		var ext: String?
			set(value) {
				addr.ext = value
			}
			get() = addr.ext
		var locality: String?
			set(value) {
				addr.locality = value
			}
			get() = addr.locality
		var region: String?
			set(value) {
				addr.region = value
			}
			get() = addr.region
		var code: String?
			set(value) {
				addr.code = value
			}
			get() = addr.code
		var country: String?
			set(value) {
				addr.country = value
			}
			get() = addr.country

		fun parameters(init: ParametersBldr.() -> Unit): Parameters {
			val res = Parameters(addr.element)
			val a = ParametersBldr(res)
			a.init()
			return res
		}

	}

	fun address(init: AddressBldr.() -> Unit): Address {
		val el = element("adr") {}
		vcard.element.add(el)
		val res = Address(el)
		val a = AddressBldr(res)
		a.init()
		if (el.children.isEmpty() && el.value.isNullOrBlank()) {
			vcard.element.remove(el)
		}
		return res
	}

	@VCardTagMarker
	class EmailBldr(private val email: Email) {

		var text: String?
			set(value) {
				email.text = value
			}
			get() = email.text

		fun parameters(init: ParametersBldr.() -> Unit): Parameters {
			val res = Parameters(email.element)
			val a = ParametersBldr(res)
			a.init()
			return res
		}

	}

	fun email(init: EmailBldr.() -> Unit): Email {
		val el = element("email") {}
		vcard.element.add(el)
		val res = Email(el)
		val a = EmailBldr(res)
		a.init()
		if (el.children.isEmpty() && el.value.isNullOrBlank()) {
			vcard.element.remove(el)
		}

		return res
	}

	@VCardTagMarker
	class OrgBldr(private val org: Organization) {

		var name: String?
			set(value) {
				org.name = value
			}
			get() = org.name

		fun parameters(init: ParametersBldr.() -> Unit): Parameters {
			val res = Parameters(org.element)
			val a = ParametersBldr(res)
			a.init()
			return res
		}

	}

	fun org(init: OrgBldr.() -> Unit): Organization {
		val el = element("org") {}
		vcard.element.add(el)
		val res = Organization(el)
		val a = OrgBldr(res)
		a.init()
		if (el.children.isEmpty() && el.value.isNullOrBlank()) {
			vcard.element.remove(el)
		}
		return res
	}

	@VCardTagMarker
	class StructuredNameBldr(private val n: StructuredName) {

		var additional: String?
			set(value) {
				n.additional = value
			}
			get() = n.additional

		var surname: String?
			set(value) {
				n.surname = value
			}
			get() = n.surname
		var given: String?
			set(value) {
				n.given = value
			}
			get() = n.given

	}

	fun structuredName(init: StructuredNameBldr.() -> Unit): StructuredName {
		val res = StructuredName()
		val a = StructuredNameBldr(res)
		a.init()
		vcard.structuredName = res
		return res
	}

	@VCardTagMarker
	class TelephoneBldr(private val tel: Telephone) {

		var uri: String?
			set(value) {
				tel.uri = value
			}
			get() = tel.uri

		fun parameters(init: ParametersBldr.() -> Unit): Parameters {
			val res = Parameters(tel.element)
			val a = ParametersBldr(res)
			a.init()
			return res
		}

	}

	fun telephone(init: TelephoneBldr.() -> Unit): Telephone {
		val el = element("tel") {}
		vcard.element.add(el)
		val res = Telephone(el)
		val a = TelephoneBldr(res)
		a.init()
		if (el.children.isEmpty() && el.value.isNullOrBlank()) {
			vcard.element.remove(el)
		}
		return res
	}

	@VCardTagMarker
	class PhotoUriBldr(private val photo: Photo.PhotoUri) {

		var uri: String?
			set(value) {
				photo.uri = value
			}
			get() = photo.uri
	}

	fun photoUri(init: PhotoUriBldr.() -> Unit): Photo.PhotoUri {
		val el = element("photo") {}
		vcard.element.add(el)
		val res = Photo.PhotoUri(el)
		val a = PhotoUriBldr(res)
		a.init()
		if (el.children.isEmpty() && el.value.isNullOrBlank()) {
			vcard.element.remove(el)
		}
		return res
	}

	@VCardTagMarker
	class PhotoDataBldr(private val photo: Photo.PhotoData) {

		var imageType: String? = photo.imageType
			set(value) {
				field = value
				photo.setData(value ?: "", data ?: "")
			}

		var data: String? = photo.data
			set(value) {
				field = value
				photo.setData(imageType ?: "", value ?: "")
			}

	}

	fun photoData(init: PhotoDataBldr.() -> Unit): Photo.PhotoData {
		val el = element("photo") {}
		vcard.element.add(el)
		val res = Photo.PhotoData(el)
		val a = PhotoDataBldr(res)
		a.init()
		if (el.children.isEmpty() && el.value.isNullOrBlank()) {
			vcard.element.remove(el)
		}
		return res
	}

}

@VCardTagMarker
fun vcard(init: VCB.() -> Unit): VCard {
	val vcard = VCB(VCard(element("vcard") {
		xmlns = VCardModule.XMLNS
	}))
	vcard.init()
	return vcard.vcard
}