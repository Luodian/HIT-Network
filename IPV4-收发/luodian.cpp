#include "sysInclude.h"

extern void ip_DiscardPkt(char* pBuffer,int type);

extern void ip_SendtoLower(char*pBuffer,int length);

extern void ip_SendtoUp(char *pBuffer,int length);

extern unsigned int getIpv4Address();

// 16位累加的反码运算
unsigned short _checksum(char *pBuffer)
{
    int sum = 0;
    for(int i = 0; i < 10; ++i)
    {
        if(i != 5)
        {
            sum += ((unsigned short*)pBuffer)[i];
        }
    }
    while(sum > 0xffff)
    {
        sum = (sum & 0xffff) + (sum >> 16); 
    }
    return (unsigned short)(0xffff - sum);
}

int stud_ip_recv(char *pBuffer,unsigned short length)
{
    //32位编译器�?个char�?2bit
    unsigned short version = pBuffer[0] >> 4;
    // pirntf("version is : %s",version);
    // 版本（Version�?
    // 版本字段�?bit，通信双方使用的版本必须一致。对于IPv4，字段的值是4�?
    if(version != 4)
    {
        ip_DiscardPkt(pBuffer, STUD_IP_TEST_VERSION_ERROR);
        return 1;
    }

    // 取出�?�?
    // 首部长度（Internet Header Length�?IHL�?
    // �?bit，首部长度说明首部有多少32位字�?字节）�?
    // 由于IPv4首部可能包含数目不定的选项，这个字段也用来确定数据的偏移量�?
    // 这个字段的最小值是5（二进制0101），相当�?*4=20字节（RFC 791），最大十进制值是15�?
    unsigned short headlen = pBuffer[0] & 15;
    if(headlen != 5)
    {
        ip_DiscardPkt(pBuffer, STUD_IP_TEST_HEADLEN_ERROR);
        return 1;
    }

    // 存活时间（Time To Live，TTL�?
    // 这个8位字段避免报文在互联网中永远存在（例如陷入路由环路）�?
    // 存活时间以秒为单位，但小于一秒的时间均向上取整到一秒�?
    // 在现实中，这实际上成了一个跳数计数器：报文经过的每个路由器都将此字段�?，当此字段等�?时，报文不再向下一跳传送并被丢弃，最大值是255�?
    // 常规地，一份ICMP报文被发回报文发送端说明其发送的报文已被丢弃。这也是traceroute的核心原理�?
    unsigned short ttl = pBuffer[8];
    if(ttl == 0)
    {
        ip_DiscardPkt(pBuffer, STUD_IP_TEST_TTL_ERROR);
        return 1;
    }

    // ntohl()指的是ntohl函数，是将一个无符号长整形数从网络字节顺序转换为主机字节顺序�?ntohl()返回一个以主机字节顺序表达的数�?
    // 这里要使用ntohl()之后,才能和getIpv4Address的结果做对比
    unsigned int packet_destination = ntohl(((unsigned int*)pBuffer)[4]);
    if(getIpv4Address() != packet_destination)
    {
        ip_DiscardPkt(pBuffer, STUD_IP_TEST_DESTINATION_ERROR);
        return 1;
    }

    // 16位累加的反码运算
    int checksum = ((unsigned short *)pBuffer)[5];
    if(checksum != _checksum(pBuffer))
    {
        ip_DiscardPkt(pBuffer, STUD_IP_TEST_CHECKSUM_ERROR);
        return 1;
    }

    ip_SendtoUp(pBuffer, length);

    return 0;
}

int stud_ip_Upsend(char *pBuffer,unsigned short len,unsigned int srcAddr,
                   unsigned int dstAddr,byte protocol,byte ttl)
{
    unsigned short totallen = len + 20;
    // 构造一个报文总长的数�?
    char *pSend = (char*)malloc(sizeof(char)*(totallen));

    //version headlength
    pSend[0] = 'E';

    //total length
    unsigned short nslen = htons(totallen);
    memcpy(pSend + 2, &nslen, sizeof(unsigned short));

    //time to live
    pSend[8] = ttl;

    //protocal
    pSend[9] = protocol;

    //source address
    unsigned int source_add = htonl(srcAddr);
    memcpy(pSend + 12, &source_add, sizeof(unsigned int));

    //destination address
    unsigned int dest_add = htonl(dstAddr);
    memcpy(pSend + 16, &dest_add, sizeof(unsigned int));

    //checksum
    unsigned short checksum = _checksum(pSend);
    memcpy(pSend + 10, &checksum, sizeof(short));

    //data
    memcpy(pSend + 20, pBuffer, len);

    ip_SendtoLower(pSend,totallen);
    return 0;
}