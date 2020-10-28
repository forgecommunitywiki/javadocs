
echo "Generating forge sources"

cd .forgedev
call gradlew

call gradlew setup

echo "Copying forge sources"

cd ..

robocopy "./.forgedev/projects/forge/src/main/java" "./java" /e /eta /ns /nc /nfl /ndl