/*
   Copyright 2017 Nationale-Nederlanden

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.configuration.classloaders;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLStreamHandler;

import org.apache.log4j.Logger;

import nl.nn.adapterframework.util.LogUtil;
import nl.nn.adapterframework.util.XmlUtils;

public class DummyClassLoader extends ClassLoader {
	protected Logger log = LogUtil.getLogger(this);

	private String configurationName;
	private String configurationFileName;

	public DummyClassLoader(String configurationName,
			String configurationFileName) {
		super(DummyClassLoader.class.getClassLoader());
		this.configurationName = configurationName;
		this.configurationFileName = configurationFileName;
	}

	@Override
	public URL getResource(String name) {
		if (name.equals(configurationFileName)) {
			String config = "<configuration name=\""
					+ XmlUtils.encodeChars(configurationName) + "\" />";
			byte[] bytes = config.getBytes();
			if (bytes != null) {
				URLStreamHandler urlStreamHandler = new BytesURLStreamHandler(
						bytes);
				try {
					return new URL(null, "bytesclassloader:" + name,
							urlStreamHandler);
				} catch (MalformedURLException e) {
					log.error("Could not create url", e);
				}
			}

		}
		return super.getResource(name);
	}
}