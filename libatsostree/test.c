#include <stdio.h>
#include <gio/gio.h>
#include <ostree.h>
#include <jni.h>



int main() {
  printf("HI\n");

  GFile* file = NULL;
  
  file = g_file_new_for_path("/home/simao/ats/ostree/myrepo-partial");

  gboolean exists = g_file_query_exists(file, NULL);

  OstreeRepo* repo = ostree_repo_new(file);

  g_autoptr(GVariant) commit = NULL;

  GError *error = NULL;

  gboolean suc = ostree_repo_open(repo, NULL, &error);

  if(suc != TRUE) {
    printf("could not open repo: %s\n", error-> message);
    return -1;
  }

  suc = ostree_repo_load_variant_if_exists(repo,
                                           OSTREE_OBJECT_TYPE_COMMIT,
                                           "1b5c3ec7bbd4068623075186c97686783260ec89e7f78f8d2164bc57f8da9914",
                                           //"4f32315f837a63f5cf307026622464d5c86977c177ec7d2095b9d6567371b4f6",
                                           &commit,
                                           &error);
  if(!suc || !commit) {
    printf("Coult not load that shit\n");
    return -1;
  }

  printf("loaded that shit?\n");

  printf("commit parent: %s\n", ostree_commit_get_parent(commit));

  return 0;
}
