/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013-2018 MeBigFatGuy.com
 * Copyright 2013-2018 Dave Brosius
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations
 * under the License.
 */
package com.mebigfatguy.yank;

import java.io.Closeable;
import java.net.HttpURLConnection;

public class Closer {

    private Closer() {
    }

    public static void close(Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (Exception e) {
        	//don't care
        }
    }

    public static void close(HttpURLConnection c) {
        try {
            if (c != null) {
                c.disconnect();
            }
        } catch (Exception e) {
        	//don't care
        }
    }
}
