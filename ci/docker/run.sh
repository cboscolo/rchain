#!/usr/bin/env bash
# Prep CI Enviroment
if [[ "${CI}" = "true" ]]; then
    set -exo pipefail
fi

# Receive and set variables.
if [[ "${TRAVIS}" = "true" ]]; then
    echo "Running build on Travis CI"
    branch_name="dev"
    git_repo="https://github.com/rchain/rchain"
    docker_dst_repo="rchain/rnode:${TRAVIS_BRANCH}"
elif [[ $1 && $2 && $3 ]]; then
    echo "Running custom build"
    branch_name=$1
    git_repo=$2
    docker_dst_repo="$3"
else
    echo "Invalid number of parameters."
    echo "Example: sudo $0 <branch name> <repo url> <docker hub repo:tag>"
    echo "Example: sudo $0 dev https://github.com/rchain/rchain myrepo/rnode:mytagname"
    echo "You will be asked for you Docker repo user/password." 
    exit
fi

## Prep docker image 
# PUSHER_DOCKER_NAME="rchain-pusher-$(mktemp | awk -F. '{print $2}')"
# Use the above vs below if you want unique build names every time. 
# If build fails before last line with "rm -f" this can leave garbage containers running. 
PUSHER_DOCKER_NAME="rchain-pusher-tmp"

# If container exists force remove it.
if [[ $(docker ps -aq -f name=${PUSHER_DOCKER_NAME}) ]]; then
    docker rm -f ${PUSHER_DOCKER_NAME}
fi

# Copy and run build and push docker script in docker pusher container from above.
docker cp rchain-docker-build-push.sh ${PUSHER_DOCKER_NAME}:/ 
if [[ "${TRAVIS}" = "true" ]]; then
    # Start docker container with access to docker.sock so it can run view/run docker images.
    docker run -dit -v /var/run/docker.sock:/var/run/docker.sock \
        -e DOCKER_USERNAME="${DOCKER_USERNAME}" \
        -e DOCKER_PASSWORD="${DOCKER_PASSWORD}" \
        -e TRAVIS="${TRAVIS}" -e TRAVIS_BRANCH=${TRAVIS_BRANCH} \
        --name ${PUSHER_DOCKER_NAME} ubuntu:16.04
    # Be aware of what "-v /var/run/docker.sock:/var/run/docker.sock" is doing above.
    # See https://jpetazzo.github.io/2015/09/03/do-not-use-docker-in-docker-for-ci/.

    echo "Running Travis build and will push to Docker Hub repo deppending on branch name."
    docker exec -it ${PUSHER_DOCKER_NAME} bash -c "./rchain-docker-build-push.sh \
        ${branch_name} ${git_repo} ${docker_dst_repo}"
else
    echo "Running local build and push to Docker repo."
    docker run -dit -v /var/run/docker.sock:/var/run/docker.sock \
        --name ${PUSHER_DOCKER_NAME} ubuntu:16.04
    docker exec -it ${PUSHER_DOCKER_NAME} bash -c "./rchain-docker-build-push.sh \
        ${branch_name} ${git_repo} ${docker_dst_repo}"
fi

# Clean up
docker rm -f ${PUSHER_DOCKER_NAME}
