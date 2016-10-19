# Treehub

`ostree` repository storage for ATS Garage.

Currently a very simple version of this service is implemented. Only
POST of refs and objects is supported and no verification on the
objects is done by client or server.

- No authentication mechanism is implemented.

- No storage of static deltas.

- Object binary blobs are stored in a mysql database, which limits the
  number of objects that can be accepted and the size of the blobs.

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
        
4. Push your local `ostree` repository to treehub, make sure
   `bin/push` points to the `push` file present in this repository:
    
        bin/push myrepo-developer \
        http://treehub-staging.gw.prod01.internal.advancedtelematic.com/ somedeveloper

5. You can now pull your changes in another machine, acting as the
   client, or the same machine, to test the changes:
   
        ostree --repo=myrepo-client init --mode=archive-z2
   
        ostree --repo=myrepo-client remote add --no-gpg-verify garage \
          http://somedeveloper:somedeveloper@treehub-staging.gw.prod01.internal.advancedtelematic.com/api/v1 master
     
        ostree --repo=myrepo-client pull garage

6. You now have your changes locally and you can checkout the files:

        ostree --repo=myrepo-client checkout master checkout
   
        cat checkout/myfile.txt

        This is my file. There are many files like this, but this one is mine.

