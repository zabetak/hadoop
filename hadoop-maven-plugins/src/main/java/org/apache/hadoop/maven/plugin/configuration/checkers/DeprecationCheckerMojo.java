/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.maven.plugin.configuration.checkers;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.objectweb.asm.ClassReader;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Mojo(name = "config-deprecation-checker", defaultPhase = LifecyclePhase.VERIFY, aggregator = true, threadSafe = true)
public final class DeprecationCheckerMojo extends AbstractMojo {

    private static final String OWNER = "org/apache/hadoop/conf/Configuration";
    private static final String METHOD = "addDeprecations";

    @Parameter(defaultValue = "${reactorProjects}", readonly = true)
    private List<MavenProject> projects;

    @Parameter(property = "failOnViolation", defaultValue = "true")
    private boolean failOnViolation;

    @Override
    public void execute() {
        Set<String> deprecations = new HashSet<>();
        long startTime = System.currentTimeMillis();
        for (MavenProject p : projects) {
            Path out = Paths.get(p.getBuild().getOutputDirectory());
            if (!Files.isDirectory(out)) continue;

            scan(out, p, deprecations);
        }
        long endTime = System.currentTimeMillis();
        getLog().info("Collected deprecated properties in " + (endTime - startTime) + " ms");
        startTime = System.currentTimeMillis();
        for (MavenProject proj : projects) {
            Path out = Paths.get(proj.getBuild().getOutputDirectory());
            if (!Files.isDirectory(out)) continue;

            try (Stream<Path> paths = Files.walk(out)) {
                paths.filter(p -> p.toString().endsWith("default.xml")).forEach(confFile -> {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    try {
                        DocumentBuilder db = dbf.newDocumentBuilder();
                        Document doc = db.parse(confFile.toFile());
                        NodeList props = doc.getElementsByTagName("name");
                        for (int i = 0; i < props.getLength(); ++i) {
                            String prop = props.item(i).getTextContent();
                            if (deprecations.contains(prop)) {
                                getLog().info("File " + confFile + " contains deprecated property: " + prop);
                            }
                        }
                    } catch (ParserConfigurationException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } catch (SAXException e) {
                        throw new RuntimeException(e);
                    }
                });

            } catch (IOException e) {
                throw new RuntimeException("Failed to walk output directory: " + out, e);
            }
        }
        endTime = System.currentTimeMillis();
        getLog().info("Checked configuration files for deprecations in " + (endTime - startTime) + " ms");
    }

    void scan(Path out, MavenProject project, Set<String> deprecations) {
        getLog().info("Scanning " + project.getGroupId() + ":" + project.getArtifactId());
        try (Stream<Path> paths = Files.walk(out)) {
            paths.filter(p -> p.toString().endsWith(".class")).forEach(classFile -> {
                try {
                    byte[] bytes = Files.readAllBytes(classFile);
                    ClassReader cr = new ClassReader(bytes);

                    cr.accept(new DeprecationDeltaVisitor(deprecations), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

                } catch (IOException e) {
                    throw new RuntimeException("Failed to scan " + classFile, e);
                }
            });

        } catch (IOException e) {
            throw new RuntimeException("Failed to walk output directory: " + out, e);
        }
    }

}