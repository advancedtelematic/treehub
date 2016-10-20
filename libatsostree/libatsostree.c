#include <string.h>
#include <gio/gio.h>
#include <glib.h>
#include <glib/gstdio.h>
#include <ostree.h>
#include <stdlib.h>

char* parentOf(char* repoPath, char* commitHash, char* error_res) {
  GError *error = NULL;
  gboolean suc = FALSE;

  GFile* file =  g_file_new_for_path(repoPath);
  OstreeRepo* repo = ostree_repo_new(file);
  GVariant* commit = NULL;

  suc = ostree_repo_open(repo, NULL, &error);

  if(error != NULL) {
    strcpy(error_res, error->message);
    g_error_free(error);
    g_object_unref(file);
    return NULL;
  }

  suc = ostree_repo_load_variant_if_exists(repo,
                                           OSTREE_OBJECT_TYPE_COMMIT,
                                           commitHash,
                                           &commit,
                                           &error);
  if(error != NULL) {
    strcpy(error_res, error->message);
    g_error_free(error);
    g_object_unref(file);
    return NULL;
  }

  if(!suc || !commit) {
    strcpy(error_res, "no such commit");
    g_error_free(error);
    g_object_unref(file);
    return NULL;
  }

  char* res = ostree_commit_get_parent(commit);

  g_object_unref(file);
  g_variant_unref(commit);

  return res;
}
