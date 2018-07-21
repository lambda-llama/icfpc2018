#/usr/bin/env sh
set -ex

PRIVATE_ID=3d776dc473c346dc908333ea0288d6dd
NAME=$(date "+%d%b-%H:%M")

zip --password $PRIVATE_ID submission.zip submission/*

pushd ./icfpc2018-submissions

git pull --rebase
mv ../submission.zip ./$NAME.zip
SHA_256=$(sha256sum ./$NAME.zip | awk '{print $1}')
git add .
git commit -am"Submit"
git push
COMMIT=$(git rev-parse HEAD)
URL="https://github.com/matklad/icfpc2018-submissions/raw/$COMMIT/$NAME.zip"

curl -L \
  --data-urlencode action=submit \
  --data-urlencode privateID=$PRIVATE_ID \
  --data-urlencode submissionURL=$URL \
  --data-urlencode submissionSHA=$SHA_256 \
  https://script.google.com/macros/s/AKfycbzQ7Etsj7NXCN5thGthCvApancl5vni5SFsb1UoKgZQwTzXlrH7/exec

popd

