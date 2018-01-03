# Treehub

Treehub implements an `ostree` repository storage for over the air
updates. This project is part of [ota-community-edition][1].

This project implements an HTTP api that `ostree` can use to natively
pull objects and revisions to update an `ostree` repository.

An HTTP api is provided to receive `ostree` repository objects and
refs from command line tools such as `garage-push`, included with
[sota-tools](https://github.com/advancedtelematic/sota-tools).

## Running

Edit `application.conf` and run `sbt run`.

Check [ota-community-edition][1] for documentation on how to run this project as part of ota-community-edition.

## Testing

To test the current implementation, the following steps can be followed:

1. Install `ostree`

   This can be done with your package manager, for example:

        apt-get install ostree
  
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

## License

This code is licensed under the [Mozilla Public License 2.0](LICENSE), a copy of which can be found in this repository. All code is copyright [ATS Advanced Telematic Systems GmbH](https://www.advancedtelematic.com), 2016-2018.

[1]: https://github.com/advancedtelematic/ota-community-edition
