@file:Suppress("UnnecessaryVariable")

package tigase.halcyon.serializer.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import tigase.halcyon.core.xmpp.forms.FormField
import java.io.OutputStream

fun KSClassDeclaration.isCollection(): Boolean {
	return this.superTypes.any {
		it.resolve().declaration.qualifiedName?.asString() == "kotlin.collections.Collection"
	}
}

fun KSClassDeclaration.getFormFieldType() = if (classKind == ClassKind.ENUM_CLASS) {
	"ListSingle"
} else if (qualifiedName?.asString() == "tigase.halcyon.core.xmpp.BareJID") {
	"JidSingle"
} else if (qualifiedName?.asString() == "tigase.halcyon.core.xmpp.JID") {
	"JidSingle"
} else if (this.isCollection()) {
	"TextMulti"
} else {
	"TextSingle"
}

/**
 * This processor handles interfaces annotated with @Function.
 * It generates the function for each annotated interface. For each property of the interface it adds an argument for
 * the generated function with the same type and name.
 *
 * For example, the following code:
 *
 * ```kotlin
 * @Function(name = "myFunction")
 * interface MyFunction {
 *     val arg1: String
 *     val arg2: List<List<*>>
 * }
 * ```
 *
 * Will generate the corresponding function:
 *
 * ```kotlin
 * fun myFunction(
 *     arg1: String,
 *     arg2: List<List<*>>
 * ) {
 *     println("Hello from myFunction")
 * }
 * ```
 */
class FunctionProcessor(
	private val options: Map<String, String>,
	private val logger: KSPLogger,
	private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

	operator fun OutputStream.plusAssign(str: String) {
		this.write(str.toByteArray())
	}

	@OptIn(KspExperimental::class)
	override fun process(resolver: Resolver): List<KSAnnotated> {
//		val symbols=resolver.getNewFiles()
//			.map { it.packageName }
//			.map { resolver.getDeclarationsFromPackage(it.asString()) }
//			.flatMap { it }
//			.toSet()
//			.filterIsInstance<KSClassDeclaration>()
//			.filter {
//				it.superTypes.any { it.resolve().declaration.qualifiedName?.asString() == "tigase.halcyon.core.xmpp.forms.DataFormWrapper" }
//			}
//		logger.info("Found ${symbols.size} classes.")

		val symbols = resolver
			// Getting all symbols that are annotated with @Function.
			.getSymbolsWithAnnotation("tigase.halcyon.core.xmpp.forms.SerializableDataForm")
			// Making sure we take only class declarations.
			.filterIsInstance<KSClassDeclaration>()

		// Exit from the processor in case nothing is annotated with @Function.
		if (!symbols.iterator()
				.hasNext()
		) return emptyList()


		symbols.forEach { declaration ->
			val file = codeGenerator.createNewFile(
				dependencies = Dependencies(
					false,
					*resolver.getAllFiles()
						.toList()
						.toTypedArray()
				),
				packageName = declaration.qualifiedName!!.getQualifier(),
				fileName = declaration.qualifiedName!!.getShortName() + "_Serializer"
			)

			file += "package ${declaration.qualifiedName!!.getQualifier()}\n\n"
			file += "import tigase.halcyon.core.xmpp.forms.*\n"
			file += "import tigase.halcyon.core.xml.Element\n"
			file += "import tigase.halcyon.core.xml.element\n"

			declaration.accept(GenerateAsSubmitElementVisitor(file), Unit)
			declaration.accept(GenerateParserVisitor(file), Unit)

			file.close()
		}

//		// The generated file will be located at:
//		// build/generated/ksp/main/kotlin/com/morfly/GeneratedFunctions.kt
//		val file = codeGenerator.createNewFile(
//			// Make sure to associate the generated file with sources to keep/maintain it across incremental builds.
//			// Learn more about incremental processing in KSP from the official docs:
//			// https://kotlinlang.org/docs/ksp-incremental.html
//			dependencies = Dependencies(
//				false,
//				*resolver.getAllFiles()
//					.toList()
//					.toTypedArray()
//			), packageName = "com.morfly", fileName = "GeneratedFunctions"
//		)
//		// Generating package statement.
//		file += "package com.morfly\n"
//
//		// Processing each class declaration, annotated with @Function.
//		symbols.forEach { it.accept(Visitor(file), Unit) }
//
//		// Don't forget to close the out stream.
//		file.close()

		val unableToProcess = symbols.filterNot { it.validate() }
			.toList()
		return unableToProcess
	}

	inner class GenerateParserVisitor(private val file: OutputStream) : KSVisitorVoid(){

		override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
			file += "fun ${classDeclaration.qualifiedName!!.getShortName()}.parse(): ${classDeclaration.qualifiedName!!.getShortName()} = "



			file += "}\n"
		}

	}

	inner class GenerateAsSubmitElementVisitor(private val file: OutputStream) : KSVisitorVoid() {

		@OptIn(KspExperimental::class)
		override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
			file += "fun ${classDeclaration.qualifiedName!!.getShortName()}.asSubmitElement() = element(\"x\"){\n"
			file += """
			xmlns = "jabber:x:data"
			attributes["type"] = "submit"
	
			""".trimIndent()

			val properties = classDeclaration.getAllProperties()
				.filter { it.validate() }
				.filter { it.type.resolve().declaration is KSClassDeclaration }
				.filter {
					it.isAnnotationPresent(FormField::class)
				}

			properties.forEach { prop ->
				visitPropertyDeclaration(prop, Unit)

			}
			file += "}\n"
		}

		@OptIn(KspExperimental::class)
		override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
			val annotation = property.getAnnotationsByType(FormField::class)
				.first()
			val declaration = property.type.resolve().declaration as KSClassDeclaration

			val xmppFieldType = declaration.getFormFieldType()

			file += """
					(fields["${annotation.name}"] ?: FieldMetadata("${annotation.name}", null, FieldType.$xmppFieldType, false)).let { f ->
						addChild(createFieldElement(this@asSubmitElement.${property.simpleName.asString()}, f.name, f))
					}
					
				""".trimIndent()
		}

	}
}