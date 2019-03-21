#!/bin/bash

#GATEWAY_URL=<your gateway url>
STAT_NUM=30

test_lb() {
    if [[ -z "${GATEWAY_URL}" ]]; then
        echo "missing env GATEWAY_URL"
        return -1
    fi

    cookie=""
    genCookie=false
    firstReq=true
    if [ ${COOKIE} ]; then
        if [ ${COOKIE} ==  "AUTO" ]; then
            genCookie=true
        else 
            cookie=${COOKIE}
        fi
    fi

    # list the pods
    str=$((kubectl get pod -n ns1; kubectl get pod -n ns2)| grep managed-server | awk '{ print $1 }')
    str1=${str// / }
    pod_names=($str1)
    pod_number=${#pod_names[@]}

    # responses is to store the latest $STAT_NUM responses for stat
    declare -a responses
    for ((j=0;j<$STAT_NUM;j++))
    do
        responses[$j]=-1
    done
    
    currentPos=0

    echo "curl -i http://$GATEWAY_URL/testwebapp/ -H "cookie: $cookie" ..."
    ## TODO: put it to function
    if [ $genCookie == false ]; then
        echo "--------stat of the latest $STAT_NUM requests: percent%(access count)--------"
        for ((j=0;j<$pod_number;j++))
        do
            echo -ne "${pod_names[j]}\t"
        done
        echo -ne "\n"
    fi

    while true
    do
        content=$(curl -i http://$GATEWAY_URL/testwebapp/ -H "cookie: $cookie" 2> /dev/null)
        currentServer=""
        for ((j=0;j<$pod_number;j++))
        do
            currentServer=${pod_names[j]}
            echo $content | grep $currentServer > /dev/null
            if [ $? -eq 0 ]; then
                responses[$currentPos]=$j
                currentPos=$((currentPos+1))
                if [ $((currentPos)) -eq $STAT_NUM ]; then
                    currentPos=0
                fi
                if $genCookie && $firstReq; then
                    echo "response:"
                    echo -ne "\tfrom $currentServer\n"
                fi
                break
            fi
        done

        if $genCookie; then
            leftover=$content
            while true
            do
                echo $leftover | grep "set-cookie: " > /dev/null
                if [ $? -ne 0 ]; then
                    if [[ $firstReq && "$cookie" == "" ]]; then
                        echo "\n\tError exit without set-cookie headers"
                        exit 1
                    fi
                    break
                fi
                leftover=${leftover#*set-cookie: }
                oneCookie=${leftover%%;*}
                if [ $firstReq == false ]; then
                    echo -ne "\n\nError exit with unexpected response set-cookie: $oneCookie from $currentServer\n"
                    exit 1
                fi
                echo -ne "\twith header set-cookie: $oneCookie\n"
                echo $cookie | grep $oneCookie > /dev/null
                if [ $? -ne 0 ]; then
                    if [ "$cookie" != "" ]; then
                        cookie=$cookie"; "
                    fi
                    cookie=$cookie$oneCookie
                fi
            done
            if $firstReq; then
                echo -ne "\ncurl -i http://$GATEWAY_URL/testwebapp/ -H "cookie: $cookie" ...\n"
                echo "--------stat of the latest $STAT_NUM requests: percent%(access count) --------"
                for ((j=0;j<$pod_number;j++))
                do
                    echo -ne "${pod_names[j]}\t"
                done
                echo -ne "\n"
                firstReq=false
            fi
        fi

        resp_num=0
        # pod_counts is to calculate how many requests each pod handled
        declare -a pod_counts
        for ((j=0;j<$pod_number;j++))
        do
            pod_counts[$j]=0
        done
        for ((j=0;j<$STAT_NUM;j++))
        do
            pod=${responses[j]}
            if [ $((pod)) -ge 0 ]; then
                resp_num=$((resp_num+1))
                count=pod_counts[$pod]
                pod_counts[$pod]=$((count+1))
            fi
        done
        echo -ne "\r"
        for ((i=0;i<$pod_number;i++))
        do
            count=${pod_counts[i]}
            percent=$[count*100/resp_num]
            printf "%5d%%(%2d)\t\t" $percent $count
        done
    done

    return 0
}


test_lb

