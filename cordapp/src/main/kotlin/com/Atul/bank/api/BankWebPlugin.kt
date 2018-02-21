package com.Atul.bank.api

import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class BankWebPlugin : WebServerPluginRegistry {
    // A list of classes that expose web APIs.
    override val webApis = listOf(Function(::BankWebApi))
}

