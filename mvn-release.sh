#!/bin/sh -e

echo "Preparing release..."
mvn release:clean release:prepare

echo "Performing release..."
mvn release:perform -Darguments=-Dgpg.passphrase=

echo "Updating site..."
cd target/checkout/ && mvn site -DdeployGitHubSite=true && cd ../../

echo "*** Release completed successfully ***"
