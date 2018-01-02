# Treehub

`ostree` repository storage for ATS Garage.

- No storage of static deltas.

- The client implementation is a very hacky bash script.

## Testing

To test the current implementation, the following steps can be followed:

1. Install `ostree`

   This can be done with your package manager, for example:

        pacman -S ostree
  
2. Create a ostree repository

        ostree --repo=myrepo-developer init --mode=archive-z2
   
3. Commit a filesystem tree to the repository

        mkdir developer-files
        echo "This is my file. There are many files like this, but this one is mine." > developer-files/myfile.txt
    
        ostree --repo=myrepo-developer commit --subject 'created my new shiny file' \
          --branch=master --tree=dir=developer-files
        

4. Push your local `ostree` repository to treehub using the `garage-push` tool.

        sudo apt-get install build-essential cmake g++ libboost-dev libboost-program-options-dev libboost-filesystem-dev libboost-system-dev libcurl4-gnutls-dev
        git clone https://github.com/advancedtelematic/sota-tools
        cd sota-tools
        mkdir build
        cd build
        cmake -DCMAKE_BUILD_TYPE=Debug ..
        make garage-push
        ./garage-push --repo myrepo-developer --ref master --user somedeveloper

5. You can now pull your changes in another machine, acting as the
   client, or the same machine, to test the changes.
   
        ostree --repo=myrepo-client init --mode=archive-z2
   
        ostree --repo=myrepo-client remote add \
          --no-gpg-verify garage \
          https://treehub-staging.gw.prod01.advancedtelematic.com/api/v2/mydevice master
     
        ostree --repo=myrepo-client pull \
        --http-header="Authorization=Bearer $DEVICE_TOKEN" garage

6. You now have your changes locally and you can checkout the files:

        ostree --repo=myrepo-client checkout master checkout
   
        cat checkout/myfile.txt

        This is my file. There are many files like this, but this one is mine.


## Release

A treehub release consists of two steps:

1. Create git tags and push them to github

2. Create a docker image and publish it to docker hub

These steps are automated in teamcity. To trigger a new release, you
can merge master to release and push:

    git checkout release
    git merge master --ff-only
    git push origin
    
## Deploy to stable

Deploying a commit to stable is independent of the release
process. This means you can deploy a commit that was not previously
released as a git tag. In this case, the deployed image version will
have the format `0.0.0-g<commit>` instead of a released version format
(`0.0.0`).

To deploy to stable, you should first release the commit (see above)
and then merge to stable:

    git fetch
    git checkout stable
    git reset --hard origin/stable
    git merge origin/release --ff-only
    git push
    
This will build a docker image and deploy it to mesos.

## License

This code is licensed under the [Mozilla Public License 2.0](LICENSE), a copy of which can be found in this repository. All code is copyright [ATS Advanced Telematic Systems GmbH](https://www.advancedtelematic.com), 2016-2018.
