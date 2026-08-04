#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# google-services.json is gitignored and required unconditionally by the
# google-services Gradle plugin (app/build.gradle). CI falls back to this
# same dummy config when the real Firebase secret isn't available; reuse
# it here so Gradle builds can configure in a remote session too. Keep the
# two in sync - build.yml carries this file base64-encoded.
#
# One client per applicationId the build produces, suffixes included, or the
# plugin fails that variant with "No matching client found for package name".
# The client_type 3 oauth_client is what generates default_web_client_id,
# which IdentityModule.kt reads unconditionally.
target="$CLAUDE_PROJECT_DIR/app/google-services.json"
if [ ! -f "$target" ]; then
  cat > "$target" << 'EOF'
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "dummy-project",
    "storage_bucket": "dummy-project.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000",
        "android_client_info": {
          "package_name": "com.litus_animae.refitted"
        }
      },
      "oauth_client": [
        {
          "client_id": "000000000000-dummy.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "dummy-api-key"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    },
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000001",
        "android_client_info": {
          "package_name": "com.litus_animae.refitted.debug"
        }
      },
      "oauth_client": [
        {
          "client_id": "000000000000-dummy.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "dummy-api-key"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    },
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000002",
        "android_client_info": {
          "package_name": "com.litus_animae.refitted.min_debug"
        }
      },
      "oauth_client": [
        {
          "client_id": "000000000000-dummy.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "dummy-api-key"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
EOF
fi
