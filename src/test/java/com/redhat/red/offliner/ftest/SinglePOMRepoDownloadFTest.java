/**
 * Copyright (C) 2015 Red Hat, Inc. (jcasey@redhat.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.red.offliner.ftest;

import com.redhat.red.offliner.Main;
import com.redhat.red.offliner.Options;
import com.redhat.red.offliner.ftest.fixture.TestRepositoryServer;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.junit.Test;

import java.io.File;
import java.util.Collections;

import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Create a one-dependency Maven POM containing one repository to test that the artifact can be downloaded from that
 * repository.
 *
 * Created by dcoleman on 02/07/2016.
 */
public class SinglePOMRepoDownloadFTest extends AbstractOfflinerFunctionalTest
{
    /**
     * In general, we should only have one test method per functional test. This allows for the best parallelism when we
     * execute the tests, especially if the setup takes some time.
     *
     * @throws Exception In case anything (anything at all) goes wrong!
     */
    @Test
    public void run()
        throws Exception
    {
        // We only need one repo server.
        TestRepositoryServer server = newRepositoryServer();

        // Generate some test content
        byte[] content = contentGenerator.newBinaryContent( 1024 );

        Dependency dep = contentGenerator.newDependency();
        Model pom = contentGenerator.newPom();
        pom.addDependency( dep );

        // Add a repository to the pom
        Repository repo = contentGenerator.newRepository( "Test Repo", server.getBaseUri() );
        pom.addRepository( repo );

        String path = contentGenerator.pathOf( dep );

        // Register the generated content by writing it to the path within the repo server's dir structure.
        // This way when the path is requested it can be downloaded instead of returning a 404.
        server.registerContent( path, content );
        server.registerContent( path + Main.SHA_SUFFIX, sha1Hex( content ) );
        server.registerContent( path + Main.MD5_SUFFIX, md5Hex( content ) );

        // All deps imply an accompanying POM file when using the POM artifact list reader, so we have to register one of these too.
        Model pomDep = contentGenerator.newPomFor( dep );
        String pomPath = contentGenerator.pathOf( pomDep );
        String md5Path = pomPath + Main.MD5_SUFFIX;
        String shaPath = pomPath + Main.SHA_SUFFIX;

        String pomStr = contentGenerator.pomToString( pomDep );

        server.registerContent( pomPath, pomStr );
        server.registerContent( md5Path, md5Hex( pomStr ) );
        server.registerContent( shaPath, sha1Hex( pomStr ) );

        // Write the plaintext file we'll use as input.
        File pomFile = temporaryFolder.newFile( getClass().getSimpleName() + ".pom" );

        FileUtils.write( pomFile, contentGenerator.pomToString( pom ) );

        Options opts = new Options();

        // Capture the downloads here so we can verify the content.
        File downloads = temporaryFolder.newFolder();

        opts.setDownloads( downloads );
        opts.setLocations( Collections.singletonList( pomFile.getAbsolutePath() ) );

        // run `new Main(opts).run()` and return the Main instance so we can query it for errors, etc.
        Main finishedMain = run( opts );

        assertThat( "Wrong number of downloads logged. Should have been 6 (declared jar + its corresponding POM + 2 checksums each).",
                    finishedMain.getDownloaded(), equalTo ( 6 ) );
        assertThat( "Errors should be empty!", finishedMain.getErrors().isEmpty(), equalTo( true ) );

        File downloaded = new File( downloads, path );
        assertThat( "File: " + path + " doesn't seem to be downloaded!", downloaded.exists(), equalTo( true ) );
        assertThat( "Downloaded File: " + path + " contains wrong content!",
                    FileUtils.readFileToByteArray( downloaded ), equalTo( content ) );
    }
}
