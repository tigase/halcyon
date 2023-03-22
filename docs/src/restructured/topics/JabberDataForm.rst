Jabber Data Form
================

Jabber Data Form is described in `XEP-0004 <https://xmpp.org/extensions/xep-0004.html>`__. Data forms are useful in all workflows not described in XEPs. For example service configuration or search results.

Working with forms
------------------

To access fields of received form, we have to create ``JabberDataForm`` object:

.. code:: kotlin

   val form = JabberDataForm(formElement)

Where ``formElement`` is representation of ``<x xmlns='jabber:x:data'>`` XML element.

Each form may have properties like:

-  ``type`` - `form type <https://xmpp.org/extensions/xep-0004.html#protocol-formtypes>`__,

-  ``title`` - optional title of form,

-  ``description`` - optional, human-readable, description of form.

Fields are identified by ``var`` name. Each field may have `field type <https://xmpp.org/extensions/xep-0004.html#protocol-fieldtypes>`__ (it is optional).

Let look, how to list all fields with values:

.. code:: kotlin

   val form = JabberDataForm(element)
   println("Title: ${form.title}")
   println("Description: ${form.description}")
   println("Type: ${form.type}")
   println("Fields:")
   form.getAllFields().forEach {
       println(" - ${it.fieldName}: ${it.fieldType}  (${it.fieldLabel})  == ${it.fieldValue}")
   }

To get field by name, simple use:

.. code:: kotlin

   val passwordField = form.getFieldByVar("password")

Value of those fields may be modified:

.. code:: kotlin

   passwordField.fieldValue = "******"

After all form modification, sometimes we need to send filled form back. There is separated method to prepare submit-ready form:

.. code:: kotlin

   val formElement = form.createSubmitForm()

This method prepares ``<x xmlns='jabber:x:data'>`` XML element with type ``submit`` and all fields are cleared up from unnecessary elements like descriptions or labels. It just leaves simple filed with name and value.

Creating forms
--------------

We can create new form, set title and description, and add fields:

.. code:: kotlin

   val form = JabberDataForm.create(FormType.Form)
   form.addField("username", FieldType.TextSingle)
   form.addField("password", FieldType.TextPrivate).apply {
       fieldLabel = "Enter password"
       fieldDesc = "Password must contain at least 8 characters"
       fieldRequired = true
   }

To get XML element containing form without cleaning it, just use:

.. code:: kotlin

   val formElement = form.element

Multi value response
--------------------

There is a variant of form containing many sets of fields. This kind of form has declared set of column with names and set of items containing field with names declared before.

This example shows how to display all fields with values:

.. code:: kotlin

   val form = JabberDataForm(element)
   val columns = form.getReportedColumns().mapNotNull { it.fieldName }
   columns.forEach { print("$it;  ") }
   println()
   println("------------")
   form.getItems().forEach { item ->
       columns.forEach { col -> print("${item.getValue(col).fieldValue};  ") }
       println()
   }

Creating multi value form is also simple. First we have to set list of reported columns, because when new item is added, field names are checked against declared columns.

.. code:: kotlin

   val form = JabberDataForm.create(FormType.Result)
   form.title = "Bot Configuration"
   form.setReportedColumns(listOf(Field.create("name", null), Field.create("url", null)))
   form.addItem(
       listOf(Field.create("name").apply { fieldValue = "Comune di Verona - Benvenuti nel sito ufficiale" },
              Field.create("url").apply { fieldValue = "http://www.comune.verona.it/" })
   )
   form.addItem(
       listOf(Field.create("name").apply { fieldValue = "Universita degli Studi di Verona - Home Page" },
              Field.create("url").apply { fieldValue = "http://www.univr.it/" })
   )
