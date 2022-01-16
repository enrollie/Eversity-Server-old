/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

import by.enrollie.eversity_plugins.plugin_api.BasicIntegration;
import by.enrollie.eversity_plugins.plugin_api.TPIntegration;

module server_api {
    opens by.enrollie.eversity_plugins.plugin_api;
    requires kotlin.stdlib;
    requires org.joda.time;
    exports by.enrollie.eversity_plugins.plugin_api;
    provides TPIntegration with BasicIntegration;
}
