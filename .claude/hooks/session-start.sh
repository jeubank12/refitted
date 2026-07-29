#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# google-services.json is gitignored and required unconditionally by the
# google-services Gradle plugin (app/build.gradle). CI falls back to this
# same dummy config when the real Firebase secret isn't available; reuse
# it here so Gradle builds can configure in a remote session too.
if [ ! -f app/google-services.json ]; then
  cat > app/google-services.json << 'EOF'
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
      "oauth_client": [],
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
