#!/bin/bash

##############################################################################
# Lockbox Gradle Plugins - Publish to AWS CodeArtifact
#
# This script publishes the gradle-plugins to AWS CodeArtifact releases repository.
# It handles AWS authentication and environment setup automatically.
#
# Prerequisites:
# - AWS CLI installed and configured with appropriate credentials
# - .env file created from .env.template with your configuration
# - Gradle wrapper (./gradlew) available
#
# Usage:
#   ./publish.sh                - Publish to CodeArtifact
#   ./publish.sh --publish-local - Publish to local Maven (~/.m2)
#
##############################################################################

set -e  # Exit on error
set -u  # Exit on undefined variable

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Parse command-line arguments
PUBLISH_LOCAL=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --publish-local)
            PUBLISH_LOCAL=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --publish-local  Publish to local Maven repository (~/.m2)"
            echo "  -h, --help       Show this help message"
            echo ""
            echo "Without options, publishes to AWS CodeArtifact."
            exit 0
            ;;
        *)
            echo -e "${RED}ERROR: Unknown option: $1${NC}"
            echo "Use --help for usage information."
            exit 1
            ;;
    esac
done

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Lockbox Gradle Plugins - Publish${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if .env file exists
if [ ! -f "${SCRIPT_DIR}/.env" ]; then
    echo -e "${RED}ERROR: .env file not found!${NC}"
    echo ""
    echo "Please create a .env file from the template:"
    echo "  cp .env.template .env"
    echo ""
    echo "Then edit .env with your AWS CodeArtifact configuration."
    exit 1
fi

# Load environment variables from .env file
echo -e "${BLUE}Loading configuration from .env...${NC}"
set -a  # Automatically export all variables
source "${SCRIPT_DIR}/.env"
set +a

# Validate required environment variables
REQUIRED_VARS=(
    "CODEARTIFACT_DOMAIN"
    "CODEARTIFACT_ACCOUNT_ID"
    "CODEARTIFACT_REGION"
    "CODEARTIFACT_JAVA_REPOSITORY"
)

MISSING_VARS=()
for VAR in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!VAR:-}" ]; then
        MISSING_VARS+=("$VAR")
    fi
done

if [ ${#MISSING_VARS[@]} -gt 0 ]; then
    echo -e "${RED}ERROR: Missing required environment variables in .env:${NC}"
    for VAR in "${MISSING_VARS[@]}"; do
        echo "  - $VAR"
    done
    exit 1
fi

echo -e "${GREEN}✓ Configuration loaded${NC}"
echo "  Domain: ${CODEARTIFACT_DOMAIN}"
echo "  Account: ${CODEARTIFACT_ACCOUNT_ID}"
echo "  Region: ${CODEARTIFACT_REGION}"
echo "  Repository: ${CODEARTIFACT_JAVA_REPOSITORY}"
echo ""

# Check if publishing to local Maven or CodeArtifact
if [ "${PUBLISH_LOCAL}" = "true" ]; then
    echo -e "${YELLOW}Publishing to local Maven repository (~/.m2/repository)${NC}"
    echo ""
else
    # Obtain CodeArtifact authorization token
    echo -e "${BLUE}Obtaining CodeArtifact authorization token...${NC}"
    
    if ! command -v aws &> /dev/null; then
        echo -e "${RED}ERROR: AWS CLI not found!${NC}"
        echo "Please install the AWS CLI: https://aws.amazon.com/cli/"
        exit 1
    fi
    
    CODEARTIFACT_AUTH_TOKEN=$(aws codeartifact get-authorization-token \
        --domain "${CODEARTIFACT_DOMAIN}" \
        --domain-owner "${CODEARTIFACT_ACCOUNT_ID}" \
        --region "${CODEARTIFACT_REGION}" \
        --query authorizationToken \
        --output text)
    
    if [ -z "${CODEARTIFACT_AUTH_TOKEN}" ]; then
        echo -e "${RED}ERROR: Failed to obtain CodeArtifact authorization token!${NC}"
        echo "Please check your AWS credentials and permissions."
        exit 1
    fi
    
    export CODEARTIFACT_AUTH_TOKEN
    echo -e "${GREEN}✓ Authorization token obtained${NC}"
    echo ""
fi

# Run Gradle build and publish
echo -e "${BLUE}Building and publishing gradle-plugins...${NC}"
echo ""

"${SCRIPT_DIR}/gradlew" clean build publish

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✓ Publish completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

if [ "${PUBLISH_LOCAL}" = "true" ]; then
    echo "Artifacts published to: ~/.m2/repository/io/github/lockboxai/"
else
    echo "Artifacts published to: ${CODEARTIFACT_DOMAIN}/${CODEARTIFACT_JAVA_REPOSITORY}"
fi
