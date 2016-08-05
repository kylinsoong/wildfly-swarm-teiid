package org.wildfly.swarm.teiid.modules.generator;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jboss.shrinkwrap.descriptor.spi.node.Node;
import org.jboss.shrinkwrap.descriptor.spi.node.NodeImporter;
import org.jboss.shrinkwrap.descriptor.spi.node.dom.XmlDomNodeImporterImpl;


/**
 * 
 * @author Kylin Soong
 * @since 05/08/16
 *
 */
public class Generator {
    
    private static final Logger log = Logger.getLogger(Generator.class.getName());
    
    private Path modules;
        
    private Charset charset = StandardCharsets.UTF_8;
    
    public Generator(Path modules) {
        this.modules = modules;
    }

    public static void main(String[] args) throws Exception {

        log.info("Teiid Modules: " + args[0]);
        log.info("Output: " + args[1]);
        
        Path modules = Paths.get(args[0]);
        if(!Files.exists(modules)){
            throw new IllegalArgumentException("Teiid Modules argument not exist");
        }
                        
        new Generator(modules).processGeneratorTargets();
                
    }

    public void processGeneratorTargets() throws IOException {

        Files.walkFileTree(modules, new SimpleFileVisitor<Path>(){

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                String content = new String(Files.readAllBytes(path), charset);
                
                // replace schema version
                content = content.replaceAll("urn:jboss:module:1.0", "urn:jboss:module:1.3");
                content = content.replaceAll("urn:jboss:module:1.1", "urn:jboss:module:1.3");
                
                // replace resource-root to artifact, eg, replace '<resource-root path="teiid-engine-9.1.0.Alpha2.jar" />'
                // to '<artifact name="${org.jboss.teiid:teiid-engine}" />'  
                List<String> dependencies = getArtifactsDependencies();
                Map<String, String> replacementMap = getReplacementMap(dependencies, path);
                
                Files.write(path, content.getBytes(charset));
                return super.visitFile(path, attrs);
            }});
        
        
    }
    
    protected Map<String, String> getReplacementMap(List<String> dependencies, Path path) throws IllegalArgumentException, IOException{
        
        NodeImporter importer = new XmlDomNodeImporterImpl();
        Node node = importer.importAsNode(Files.newInputStream(path), true);
        node = node.getChildren().stream().filter(n -> n.getName().equals("resources")).collect(Collectors.toList()).get(0);
        Map<String, String> replacementMap = node.getChildren().stream().map(n -> n.getAttribute("path"))
            .collect(Collectors.toMap(p -> p, p -> {
                String artifactId = p.toString();
                String replacement = "";
                if(artifactId.contains(".jar")){
                    artifactId = artifactId.substring(0, artifactId.length() - 4);
                    artifactId = artifactId.substring(0, artifactId.lastIndexOf("-"));
                    replacement = getReplacement(artifactId); 
                    System.out.println(artifactId);
                } else {
                    log.warning(path + "'s resource-root 'deployments' not a jar resource, skipped");
                }
                return replacement;
            }));
        
        return replacementMap;
    }
    
    private String getReplacement(String artifactId) {
        // TODO Auto-generated method stub
        return null;
    }

    @SuppressWarnings("unchecked")
    protected List<String> getArtifactsDependencies() throws IOException{
        
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        Model model;
        try {
            model = mavenReader.read(this.getClass().getClassLoader().getResourceAsStream("maven/pom.xml"));
        } catch (XmlPullParserException e) {
            throw new RuntimeException(e);
        }
        MavenProject project = new MavenProject(model);     
        List<Dependency> dependencies = project.getDependencies();
        return dependencies.stream().map(d -> d.getGroupId() + ":" + d.getArtifactId()).collect(Collectors.toList());
    }

}
