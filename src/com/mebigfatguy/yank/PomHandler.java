/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013 MeBigFatGuy.com
 * Copyright 2013 Dave Brosius
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

import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class PomHandler extends DefaultHandler {

    private enum State {
        NONE(""), PARENT("parent"), DEPENDENCIES("dependencies"), DEPENDENCY("dependency"), GROUP("groupId"), ARTIFACT("artifactId"), VERSION("version"), OPTIONAL("optional");

        public String elementName;
        State(String name) {
            elementName = name;
        }

        public static final State getState(String value) {
            for (State s : State.values()) {
                if (s.elementName.equals(value)) {
                    return s;
                }
            }

            return State.NONE;
        }
    };

    private List<Artifact> transitiveArtifacts;
    private State state = State.NONE;
    private StringBuilder value = new StringBuilder();
    private String group = null;
    private String artifact = null;
    private String version = null;
    private String optional = null;

    public PomHandler(List<Artifact> transitives) {
        transitiveArtifacts = transitives;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        switch (state) {
        case NONE:
            if (localName.equals(State.DEPENDENCIES.elementName)) {
                state = State.DEPENDENCIES;
            } else if (localName.equals(State.PARENT.elementName)) {
                state = State.PARENT;
            }
            break;

        case DEPENDENCIES:
            if (localName.equals(State.DEPENDENCY.elementName)) {
                state = State.DEPENDENCY;
            }
            break;

        case DEPENDENCY:
        case PARENT:
            state = State.getState(localName);
            if (state == State.NONE){
                state = State.DEPENDENCY;
            } else {
                value.setLength(0);
            }
            break;
            
        case ARTIFACT:
        case GROUP:
        case OPTIONAL:
        case VERSION:
            break;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        switch (state) {

        case DEPENDENCIES:
            if (localName.equals(State.DEPENDENCIES.elementName)) {
                throw new PomParseCompletedException();
            }
            break;

        case DEPENDENCY:
        case PARENT:
            if (localName.equals(State.PARENT.elementName)) {
                state = State.NONE;
            } else {
                state = State.DEPENDENCIES;
                if ((!"true".equals(optional)) && (group != null) && (artifact != null) && (version != null)) {
                    transitiveArtifacts.add(new Artifact(group, artifact, "", version));
                }
            }
            break;

        case GROUP:
            group = value.toString().trim();
            state = State.DEPENDENCY;
            break;

        case ARTIFACT:
            artifact = value.toString().trim();
            state = State.DEPENDENCY;
            break;

        case VERSION:
            version = value.toString().trim();
            state = State.DEPENDENCY;
            break;

        case OPTIONAL:
            optional = value.toString().trim();
            state = State.DEPENDENCY;
            break;
            
        case NONE:
            break;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        switch (state) {
        case GROUP:
        case ARTIFACT:
        case VERSION:
        case OPTIONAL:
            value.append(ch, start, length);
            break;
            
        case DEPENDENCIES:
        case DEPENDENCY:
        case NONE:
        case PARENT:
            break;
        }
    }
}
