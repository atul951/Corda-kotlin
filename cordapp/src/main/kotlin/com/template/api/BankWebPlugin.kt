package com.template.api

import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class BankWebPlugin: WebServerPluginRegistry{
    override val webApis = listOf(Function(::BankWebApi ))
}